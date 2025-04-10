import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;


public class Kokoaja2 {

	private final String skosNs = "http://www.w3.org/2004/02/skos/core#";
	private final String skosextNs = "http://purl.org/finnonto/schema/skosext#";
	private final String kokoNs ="http://www.yso.fi/onto/koko/";
	private final String kokoMetaNs ="http://www.yso.fi/onto/koko-meta/";
	private final String XSDNs = "http://www.w3.org/2001/XMLSchema#";

	private Model koko;
	private Model onto;
	private Model aikaLeimat;

	private Map<Resource, String> ontologioidenTyypitPolutMap;
	private Map<String, Resource> kokoFiLabelitResurssitMap;
	private Map<Resource, Resource> ontoKokoResurssivastaavuudetMap;
	private Map<Resource, Resource> ontoKokoResurssivastaavuudetJotkaNykyKokossaMap;

	private Map<Resource, String> urivastaavuusTimestampit;
	private String timestampNow;

	private Set<String> sallittujenPropertyjenNimiavaruudet;
	private Set<Resource> mustaLista;

	private Set<String> deprekoidut;
	private Set<String> ysoUrit;

	private int viimeisinKokoUrinLoppuosa;

	private int romautetut;

	private String[] kielet = {"fi", "sv", "en", "se"};


	public Kokoaja2(String uriVastaavuuksiePolku) {
		this.romautetut = 0;

		this.taytaSallittujenPropertyjenNimiavaruudet();
		this.koko = this.luoAihio();
		this.aikaLeimat = this.luoAihio();
		this.urivastaavuusTimestampit = new HashMap<Resource, String>();
		this.timestampNow = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
		this.lueUriVastaavuudetTiedostosta(uriVastaavuuksiePolku);

		this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap = new HashMap<Resource, Resource>();
		this.kokoFiLabelitResurssitMap = new HashMap<String, Resource>();
		this.deprekoidut = new HashSet<String>();
		this.ysoUrit = new HashSet<String>();
	}

	public Model luoAihio() {
		Model aihio = ModelFactory.createDefaultModel();
		aihio.setNsPrefix("skos", this.skosNs);
		aihio.setNsPrefix("skosext", this.skosextNs);
		aihio.setNsPrefix("koko", this.kokoNs);
		aihio.setNsPrefix("koko-meta", this.kokoMetaNs);
		aihio.setNsPrefix("xsd", this.XSDNs);
		return aihio;
	}

	public void taytaSallittujenPropertyjenNimiavaruudet() {
		this.sallittujenPropertyjenNimiavaruudet = new HashSet<String>();
		this.sallittujenPropertyjenNimiavaruudet.add(this.skosNs);
		this.sallittujenPropertyjenNimiavaruudet.add(this.skosextNs);
		this.sallittujenPropertyjenNimiavaruudet.add(DC.getURI());
		this.sallittujenPropertyjenNimiavaruudet.add(OWL.getURI());
		this.sallittujenPropertyjenNimiavaruudet.add(RDF.getURI());
		this.sallittujenPropertyjenNimiavaruudet.add(RDFS.getURI());
		this.sallittujenPropertyjenNimiavaruudet.add(DCTerms.getURI());
		this.sallittujenPropertyjenNimiavaruudet.add("http://purl.org/iso25964/skos-thes#");
	}

	public void lueUriVastaavuudetTiedostosta(String polku) {
		System.out.println("Luetaan URI-vastaavuudet tiedostosta " + polku);
		int korkeinNro = 0;
		this.ontoKokoResurssivastaavuudetMap = new HashMap<Resource, Resource>();
		try {
			BufferedReader br = new BufferedReader( new InputStreamReader( new FileInputStream(polku), "UTF8" ) );
			String rivi = br.readLine().trim();
			while (rivi != null) {
				rivi = rivi.trim();
				String[] uriTaulukko = rivi.split(" ");
				Resource ontoRes = this.koko.createResource(uriTaulukko[0].trim());
				Resource kokoRes = this.koko.createResource(uriTaulukko[1].trim());
				if (uriTaulukko.length > 2) {
					this.urivastaavuusTimestampit.put(ontoRes, uriTaulukko[2].trim());
				}
				this.ontoKokoResurssivastaavuudetMap.put(ontoRes, kokoRes);
				int uriNro = Integer.parseInt(kokoRes.getLocalName().substring(1));
				if (uriNro > korkeinNro) korkeinNro = uriNro;
				rivi = br.readLine();
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.viimeisinKokoUrinLoppuosa = korkeinNro;
	}

	public void lueYso(String ysonPolku) {
		this.onto = JenaHelpers.lueMalliModeliksi(ysonPolku); 

		// luodaan YSOConcept-tyyppiluokka
		this.luoYSOConceptTyyppiLuokkaKokoon();
		String ysoMetaNs = "http://www.yso.fi/onto/yso-meta/";
		Resource ysoConcept = this.koko.createResource(ysoMetaNs + "Concept");

		Resource ysoConceptScheme = this.koko.createResource("http://www.yso.fi/onto/yso/");
		Resource ysoDeprecatedScheme = this.koko.createResource("http://www.yso.fi/onto/yso/deprecatedconceptscheme");

		//Luodaan ensiksi lista sallituista YSO-ureista. Muita YSO-käsitteitä ei oteta KOKOssa huomioon
		ResIterator sallitut = this.onto.listResourcesWithProperty(SKOS.inScheme, ysoConceptScheme);
		while (sallitut.hasNext()) {
			Resource ysoSubj = sallitut.next();
			this.ysoUrit.add(ysoSubj.getURI());
		}
		sallitut.close();

		//Jollei YSO:n deprekoituja käsitteitä oteta huomioon, ne katoavat KOKO:sta. Otetaan ne mielummin deprekoituina mukaan
		boolean deprekoituYsoMukaan = true;

		if (deprekoituYsoMukaan) {
			sallitut = this.onto.listResourcesWithProperty(SKOS.inScheme, ysoDeprecatedScheme);
			while (sallitut.hasNext()) {
				Resource ysoSubj = sallitut.next();
				this.ysoUrit.add(ysoSubj.getURI());
			}
		} sallitut.close();

		ResIterator resIter = this.onto.listResourcesWithProperty(SKOS.inScheme, ysoConceptScheme);
		while (resIter.hasNext()) {
			Resource ysoSubj = resIter.nextResource();
			if (!this.mustaLista.contains(ysoSubj)) {
				this.lisaaResurssiKokoon(ysoSubj, ysoSubj, true);
				if (!ysoSubj.hasProperty(RDF.type, SKOSXL.Label)) // ei väitetä skosxl-labeleita ysokäsitteiksi
					this.koko.add(ysoSubj, RDF.type, ysoConcept);
			}
		} resIter.close();
		if (deprekoituYsoMukaan) {
			resIter = this.onto.listResourcesWithProperty(SKOS.inScheme, ysoDeprecatedScheme);
			while (resIter.hasNext()) {
				Resource ysoSubj = resIter.nextResource();
				if (!this.mustaLista.contains(ysoSubj)) {
					this.lisaaResurssiKokoon(ysoSubj, ysoSubj, true);
					this.koko.add(ysoSubj, RDF.type, ysoConcept);
				}
			} resIter.close();

		}

		// kaivetaan KOKOon viela isReplacedBy-tyyppiset suhteet
		StmtIterator iter = this.onto.listStatements((Resource)null, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			Resource subject = stmt.getSubject();
			Resource object = stmt.getResource();
			if (!this.mustaLista.contains(subject)) {

				//Jottei tapahtuisi jakautuvia korvaavuussuhteita, valitaan jokaiselle replaced-suhteelle vain yksi sopiva seuraaja
				object = this.haeKorvaava(subject, this.onto);
				this.koko.add(subject, DCTerms.isReplacedBy, object);
				this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.put(subject, object);
			}
		}

		//Jos uusin YSO sanoo että tätä käsitettä ei enää käytetä, otetaan siitä tieto talteen
		Resource deprecatedScheme = this.onto.createResource("http://www.yso.fi/onto/yso/deprecatedconceptscheme");
		StmtIterator i = this.onto.listStatements(null, SKOS.inScheme, deprecatedScheme);
		while (i.hasNext()) {
			this.deprekoidut.add(i.next().getSubject().getURI());
		}
	}

	private void luoYSOConceptTyyppiLuokkaKokoon() {
		String ysoMetaNs = "http://www.yso.fi/onto/yso-meta/";

		Resource ysoConcept = this.koko.createResource(ysoMetaNs + "Concept");
		Literal fiLabel = this.koko.createLiteral("YSO-käsite", "fi");
		Literal enLabel = this.koko.createLiteral("YSO Concept", "en");
		Literal svLabel = this.koko.createLiteral("Allfo-begrepp", "sv");
		this.koko.add(ysoConcept, RDF.type, OWL.Class);
		this.koko.add(ysoConcept, RDFS.label, fiLabel);
		this.koko.add(ysoConcept, RDFS.label, enLabel);
		this.koko.add(ysoConcept, RDFS.label, svLabel);
		this.koko.add(ysoConcept, RDFS.subClassOf, SKOS.Concept);
	}

	private HashMap<Resource, String> haeTietynTyyppistenResurssienLabelitMappiin(Model malli, Property labelProp, HashSet<Resource> tietynTyyppisetResurssit, String kieli) {
		HashMap<Resource, String> resurssitLabelitMap = new HashMap<Resource, String>();
		StmtIterator iter = malli.listStatements((Resource)null, labelProp, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getLanguage().equals(kieli)) {
				Resource subj = stmt.getSubject();
				if (tietynTyyppisetResurssit.contains(subj)) {
					String labelString = ((Literal)(stmt.getObject())).getLexicalForm();
					resurssitLabelitMap.put(subj, labelString);
				}
			}
		}
		return resurssitLabelitMap;
	}

	/*
	 *  primaryLabelSource on true YSOlle, jolloin YSOn prefLabelit otetaan sellaisinaan ja muille false
	 *  jolloin prefLabeleista tulee skosext:candidateLabeleita
	 */
	private void lisaaResurssiKokoon(Resource ontoSubj, Resource kokoSubj, boolean primaryLabelSource) {
		Property skosextCandidateLabel = this.onto.createProperty(skosextNs + "candidateLabel");

		HashSet<Property> propertytJoitaEiHalutaKokoon = new HashSet<Property>();
		propertytJoitaEiHalutaKokoon.add(SKOS.inScheme);
		propertytJoitaEiHalutaKokoon.add(SKOS.topConceptOf);
		propertytJoitaEiHalutaKokoon.add(SKOS.narrower);
		propertytJoitaEiHalutaKokoon.add(DCTerms.modified);
		propertytJoitaEiHalutaKokoon.add(DCTerms.created);


		//Käsitteellä pitää olla vähintään prefLabel tai isReplacedBy, jotta sitä voidaan ottaa KOKOon
		if (!this.onto.contains(ontoSubj, SKOS.prefLabel) && !this.onto.contains(ontoSubj, DCTerms.isReplacedBy)) {
			return;
		}

		//otetaan aikaleimat talteen
		this.aikaLeimat.add(ontoSubj.listProperties(DCTerms.modified));
		this.aikaLeimat.add(ontoSubj.listProperties(DCTerms.created));

		this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.put(ontoSubj, kokoSubj);
		//this.kokoFiLabelitResurssitMap.put(this.ontoFiResurssitLabelitMap.get(ontoSubj), kokoSubj);
		this.koko.add(kokoSubj, SKOS.exactMatch, ontoSubj);
		StmtIterator iter = this.onto.listStatements(ontoSubj, (Property)null, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();

			if (this.eiToivottuViittaus(stmt)) {
				continue;
			}

			if (!(stmt.getObject().isURIResource() && this.mustaLista.contains(stmt.getObject()) && this.deprekoidut.contains(stmt.getResource().toString()))) {
				if (this.sallittujenPropertyjenNimiavaruudet.contains(stmt.getPredicate().getNameSpace()))
					if (stmt.getPredicate().equals(SKOS.prefLabel)) {
						if (primaryLabelSource) {
							this.koko.add(kokoSubj, stmt.getPredicate(), stmt.getObject());
						} else {
							this.koko.add(kokoSubj, skosextCandidateLabel, stmt.getObject());
						}
					} else if (!propertytJoitaEiHalutaKokoon.contains(stmt.getPredicate())) {

						//Lisätty tarkistus siitä ettei kokoon oteta ysossa olevia kokoon viittaavia exactMatcheja

						if (stmt.getObject().isURIResource() ) {
							if ( !((Resource)stmt.getObject()).getNameSpace().equals(this.kokoNs) )
								this.koko.add(kokoSubj, stmt.getPredicate(), stmt.getObject());
						} else {
							//Literaaleille ei tällaista tarkistusta tehdä
							this.koko.add(kokoSubj, stmt.getPredicate(), stmt.getObject());
						}
					}
			}
		}
	}

	public void lueOnto(String ontonPolku, Resource ontoTyyppi) {
		System.out.println("Luetaan " + ontonPolku);
		this.onto = JenaHelpers.lueMalliModeliksi(ontonPolku);
		this.onto = this.teeExactMatcheistaKaksisuuntaisia(this.onto);

		StmtIterator iter = this.onto.listStatements(ontoTyyppi, (Property)null, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			this.koko.add(stmt);
		}

		// kaivetaan HashSettiin kaikki erikoisontologian oman tyyppiset kasitteet
		HashSet<Resource> ontonOntoTyyppisetResurssit = new HashSet<Resource>();
		ResIterator resIter = this.onto.listResourcesWithProperty(RDF.type, ontoTyyppi);
		//Resource deprecated = this.onto.createResource(skosextNs+"DeprecatedConcept");

		while (resIter.hasNext()) {
			Resource ontoSubj = resIter.nextResource();
			//lisättiin tarkistus deprekoitujen käsitteiden välttämiseksi -> tämä pois, tällainen tarkistus tulisi koskea vain YSOa - muutoin erikoisontologian käsitteen koko-vastine (siis vanha) putoaa pois kokosta

			if (!this.mustaLista.contains(ontoSubj)) {
				ontonOntoTyyppisetResurssit.add(ontoSubj);
			}
		}
		System.out.println("Lisätään KOKOon " + ontonOntoTyyppisetResurssit.size() + " " + ontoTyyppi.getURI() + " -tyyppistä resurssia.");

		// kaivetaan HashMappiin kaikki erikoisontologian suorat skos:exactMatchit
		HashMap<Resource, HashSet<Resource>> ontonExactMatchitMap = new HashMap<Resource, HashSet<Resource>>();
		iter = this.onto.listStatements((Resource)null, SKOS.exactMatch, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			Resource subj = stmt.getSubject();
			Resource obj = (Resource)stmt.getObject();
			//Lisätty tarkistus siitä ettei exactMatcheja kokoon haeta ontologioista (ts. Koko kootaan ysoon osoittavien exactMatchien perusteella)
			try {
				if (ontonOntoTyyppisetResurssit.contains(subj) &&
						!obj.getNameSpace().equals(this.kokoNs)) {
					//lisätty tarkistus siitä ettei linkata deprekoituihin resursseihin
					if ( !obj.hasProperty(DCTerms.isReplacedBy) && !this.deprekoidut.contains(obj.getURI())) {
						HashSet<Resource> matchitSet = new HashSet<Resource>();
						if (ontonExactMatchitMap.containsKey(subj)) {
							matchitSet = ontonExactMatchitMap.get(subj);
						}
						matchitSet.add((Resource)(stmt.getObject()));
						ontonExactMatchitMap.put(subj, matchitSet);
					}
				}
			} catch (NullPointerException e) { //jos data on virheellistä
				System.out.println("Tyhjä node : " + subj.getURI() + " skos:exactMatch");
			}
		}
		Vector<String> ontoUritVektori = new Vector<String>();
		resIter = this.onto.listResourcesWithProperty(RDF.type, ontoTyyppi);
		while (resIter.hasNext()) {
			Resource ontoSubj = resIter.nextResource();
			if (!this.mustaLista.contains(ontoSubj)) {
				ontoUritVektori.add(ontoSubj.getURI());
			}
		}
		Collections.sort(ontoUritVektori);
		for (String uri:ontoUritVektori) {
			Resource ontoSubj = this.onto.createResource(uri);
			Resource kokoSubj = null;

			// Tutkitaan mikä on resurssia vastaava resurssi kokossa tai jos moista ei ole, päätetään säilötäänkö vai ei
			if (this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.containsKey(ontoSubj)) {
				kokoSubj = ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.get(ontoSubj);
			} else if (ontonExactMatchitMap.containsKey(ontoSubj)) {
				for (Resource matchRes:ontonExactMatchitMap.get(ontoSubj)) {
					if (this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.containsKey(matchRes)) {
						kokoSubj = this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.get(matchRes);
					}
				}
			}

			if (kokoSubj == null) kokoSubj = ontoSubj;

			// laitetaan käsite ja siihen liittyvat triplet KOKOon
			this.lisaaResurssiKokoon(ontoSubj, kokoSubj, false);
			if (ontonExactMatchitMap.containsKey(ontoSubj)) {
				for (Resource matchRes:ontonExactMatchitMap.get(ontoSubj)) {
					this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.put(matchRes, kokoSubj);
				}
			}
		}

		// kaivetaan KOKOon viela isReplacedBy-tyyppiset suhteet
		iter = this.onto.listStatements((Resource)null, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			Resource subject = stmt.getSubject();

			//Jottei tapahtuisi jakautuvia korvaavuussuhteita, valitaan jokaiselle replaced-suhteelle vain yksi sopiva seuraaja
			Resource object = this.haeKorvaava(subject, this.onto);
			this.koko.add(subject, DCTerms.isReplacedBy, object);
		}
	}

	public Model teeExactMatcheistaKaksisuuntaisia(Model ontologia) {
		HashSet<Statement> lisattavat = new HashSet<Statement>();

		HashSet<Resource> resurssitJoillaExactMatcheja = new HashSet<Resource>();
		ResIterator resIter = ontologia.listResourcesWithProperty(SKOS.exactMatch);
		while (resIter.hasNext()) resurssitJoillaExactMatcheja.add(resIter.nextResource());

		for (Resource res:resurssitJoillaExactMatcheja) {
			HashSet<Resource> exactMatchienKohteet = new HashSet<Resource>();
			StmtIterator iter = ontologia.listStatements(res, SKOS.exactMatch, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				lisattavat.add(ontologia.createStatement((Resource)(stmt.getObject()), SKOS.exactMatch, stmt.getSubject()));
				exactMatchienKohteet.add((Resource)stmt.getObject());
			}
			for (Resource res2:exactMatchienKohteet) {
				for (Resource res3:exactMatchienKohteet) {
					if (!res2.equals(res3)) {
						lisattavat.add(ontologia.createStatement(res2, SKOS.exactMatch, res3));
						lisattavat.add(ontologia.createStatement(res3, SKOS.exactMatch, res2));
					}
				}
			}
		}

		for (Statement s:lisattavat) ontologia.add(s);
		return ontologia;
	}

	public Resource luoUusiKokoResurssi() {
		this.viimeisinKokoUrinLoppuosa++;
		Resource uusiResurssi = this.koko.createResource(this.kokoNs + "p" + this.viimeisinKokoUrinLoppuosa);
		return uusiResurssi;
	}

	private void muutaCandidateLabelitPrefJaAltLabeleiksi() {
		Property skosextCandidateLabel = this.onto.createProperty(skosextNs + "candidateLabel");

		HashSet<Statement> poistettavat = new HashSet<Statement>();
		HashSet<Statement> lisattavat = new HashSet<Statement>();

		ResIterator resIter = this.koko.listResourcesWithProperty(skosextCandidateLabel);
		while (resIter.hasNext()) {
			Resource subj = resIter.nextResource();
			HashSet<String> fiPrefLabelSet = new HashSet<String>();
			HashSet<String> fiCandidateLabelSet = new HashSet<String>();

			StmtIterator iter = this.koko.listStatements(subj, SKOS.prefLabel, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				if (stmt.getLanguage().equals("fi")) {
					fiPrefLabelSet.add(((Literal)stmt.getObject()).getLexicalForm());
					poistettavat.add(stmt);
				}
			}
			iter = this.koko.listStatements(subj, skosextCandidateLabel, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				if (stmt.getLanguage().equals("fi")) {
					fiCandidateLabelSet.add(((Literal)stmt.getObject()).getLexicalForm());
					//poistettavat.add(stmt); ei poisteta candidateLabeleita kuin vasta purittamisen jälkeen
				}
			}

			if (fiPrefLabelSet.size() > 0) {
				String prefLabelString = this.palautaPrefLabeliksiSopivin(fiPrefLabelSet);
				lisattavat.add(this.koko.createStatement(subj, SKOS.prefLabel, this.koko.createLiteral(prefLabelString, "fi")));
				fiPrefLabelSet.remove(prefLabelString);
				for (String altLabelString:fiPrefLabelSet) {
					lisattavat.add(this.koko.createStatement(subj, SKOS.altLabel, this.koko.createLiteral(altLabelString, "fi")));
				}
				for (String altLabelString:fiCandidateLabelSet) {
					lisattavat.add(this.koko.createStatement(subj, SKOS.altLabel, this.koko.createLiteral(altLabelString, "fi")));
				}
			} else if (fiCandidateLabelSet.size() > 0) {
				String prefLabelString = this.palautaPrefLabeliksiSopivin(fiCandidateLabelSet);
				lisattavat.add(this.koko.createStatement(subj, SKOS.prefLabel, this.koko.createLiteral(prefLabelString, "fi")));
				fiCandidateLabelSet.remove(prefLabelString);
				for (String altLabelString:fiCandidateLabelSet) {
					lisattavat.add(this.koko.createStatement(subj, SKOS.altLabel, this.koko.createLiteral(altLabelString, "fi")));
				}
			} else {
				// Ei loytynyt mitaan jarkevia labeleita, joten poistetaan kokonaan KOKOsta
				System.out.println("Käsitteellä " + subj.getURI() + " ei ollut jarkeviä labeleita eikä sitä lisätty KOKOon.");
				iter = this.koko.listStatements(subj, (Property)null, (RDFNode)null);
				while (iter.hasNext()) poistettavat.add(iter.nextStatement());	
			}
		}
		for (Statement stmt:poistettavat) this.koko.remove(stmt);
		for (Statement stmt:lisattavat) this.koko.add(stmt);
	}

	// palautetaan paras prefLabel joukosta kandidaatteja - ensimmäinen kriteeri on sulkutarkenteet (jos on, hyvä), toinen pituus (lyhyempi parempi), kolmas aakkosjärjestys
	public String palautaPrefLabeliksiSopivin(HashSet<String> kandidaatitSet) {
		String paras = null;
		for (String kandidaatti:kandidaatitSet) {
			if (paras == null) paras = kandidaatti;
			else if (paras.contains("(") && !kandidaatti.contains("(")) ; 
			else if (!paras.contains("(") && kandidaatti.contains("(")) paras = kandidaatti;
			else if (paras.length() > kandidaatti.length()) paras = kandidaatti;
			else if (paras.compareToIgnoreCase(kandidaatti) > 0) paras = kandidaatti;
		}
		//System.out.println(paras + " <== " + kandidaatitSet.toString());
		return paras;
	}

	private void romautaFiPrefLabelienJaVanhempienPerusteella() {

		HashMap<String, HashSet<Resource>> fiPrefLabelKokoSubjektitMap = this.tuotaPrefLabelKokoSubjektitMap("fi");

		Vector<String> fiLabelAvaimet = new Vector<String>(); 
		for (String avain:fiPrefLabelKokoSubjektitMap.keySet()) {
			fiLabelAvaimet.add(avain);
		}
		Collections.sort(fiLabelAvaimet);

		for (String fiPrefLabel:fiLabelAvaimet) {
			HashSet<Resource> subjektitSet = fiPrefLabelKokoSubjektitMap.get(fiPrefLabel);
			if (subjektitSet.size() > 1) {
				// loytyi useampi sama prefLabel - luodaan HashMap, jossa avaimina on broaderien labelit ja arvoina resurssit
				HashMap<String, HashSet<Resource>> broaderStringResSetMap = new HashMap<String, HashSet<Resource>>();
				for (Resource res:subjektitSet) {
					Vector<String> broaderitVector = new Vector<String>();
					StmtIterator iter = this.koko.listStatements(res, SKOS.broader, (RDFNode)null);
					while (iter.hasNext()) {
						Statement stmt = iter.nextStatement();
						Resource broader = (Resource)(stmt.getObject());
						if (this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.containsKey(broader))
							broader = this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.get(broader);
						String broaderinFiPrefLabel = this.haeFiPrefLabel(broader);
						if (broaderinFiPrefLabel.equals("")) {
							broaderinFiPrefLabel = this.haeFiPrefLabel(this.haeVastineRomautuksenKesken(broader));
						}
						if (!broaderitVector.contains(broaderinFiPrefLabel)) {
							if (broaderinFiPrefLabel.equals("")) System.out.println("Ongelma romautuksien labeleissa: " + broaderinFiPrefLabel + " -- " + broader.getURI() + " -- " + res.getURI() + " " + fiPrefLabel);
							broaderitVector.add(broaderinFiPrefLabel);
						}
					}
					Collections.sort(broaderitVector);
					String broaderitString = "";
					for (int i = 0; i < broaderitVector.size(); i++) {
						if (broaderitVector.get(i).trim().length() > 0) {
							broaderitString += broaderitVector.get(i);
							if (i < broaderitVector.size()-1) broaderitString += ", ";
						}
					}
					HashSet<Resource> resSet = new HashSet<Resource>();
					if (broaderStringResSetMap.containsKey(broaderitString)) resSet = broaderStringResSetMap.get(broaderitString);
					resSet.add(res);
					broaderStringResSetMap.put(broaderitString, resSet);
				}
				HashMap<String, Resource> romautettuMap = new HashMap<String, Resource>();
				for (String broaderitString:broaderStringResSetMap.keySet()) {
					HashSet<Resource> resSet = broaderStringResSetMap.get(broaderitString);
					Resource romautettuRes = null;
					if (resSet.size() > 1) {
						romautettuRes = this.romauta(resSet);
					} else {
						for (Resource res:resSet) romautettuRes = res;
					}
					// riisutaan sulkutarkenteet pois sulkutarkenteelta
					if (broaderitString.contains("(")) {
						String sulkutarkenteetonBroader = broaderitString.substring(0, broaderitString.indexOf("(")).trim();
						romautettuMap.put(sulkutarkenteetonBroader, romautettuRes);
					} else {
						romautettuMap.put(broaderitString, romautettuRes);
					}
				}
				if (romautettuMap.size() > 1) {
					this.lisaaSulkutarkenteet(romautettuMap);
				}
			}
		}
	}

	private Resource haeVastineRomautuksenKesken(Resource res) {
		Resource palautettava = res;

		StmtIterator iter = this.koko.listStatements((Resource)null, SKOS.exactMatch, res);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			palautettava = stmt.getSubject();
		}
		iter = this.koko.listStatements(res, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			palautettava = (Resource)(stmt.getObject());
		}
		return palautettava;
	}

	private String haeFiPrefLabel(Resource subj) {
		String prefLabelString = "";
		StmtIterator iter = this.koko.listStatements(subj, SKOS.prefLabel, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getLanguage().equals("fi"))
				prefLabelString = ((Literal)stmt.getObject()).getLexicalForm();
		}
		if (prefLabelString.equals("")) {
			iter = this.koko.listStatements(subj, SKOS.prefLabel, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				prefLabelString = ((Literal)stmt.getObject()).getLexicalForm();
			}
		}
		return prefLabelString;
	}

	private Resource romauta(HashSet<Resource> romautettavat) {
		this.romautetut += romautettavat.size();
		// valitaan yso-URI tai pienin URI pidettavaksi ja romautetaan loput siihen
		Resource subj = null;
		for (Resource res:romautettavat) {
			if (subj != null) {
				if (subj.getNameSpace().equals("http://www.yso.fi/onto/yso/") && !res.getNameSpace().equals("http://www.yso.fi/onto/yso/")) {

				} else if (!subj.getNameSpace().equals("http://www.yso.fi/onto/yso/") && res.getNameSpace().equals("http://www.yso.fi/onto/yso/")) {
					subj = res;
				} else if (subj.getURI().compareTo(res.getURI()) > 0) {
					subj = res;
				}
			} else {
				subj = res;
			}
		}
		romautettavat.remove(subj);
		for (Resource poistuva:romautettavat) {
			this.muutaKokoSubj(poistuva, subj);
			this.koko.add(subj, SKOS.exactMatch, poistuva);
		}
		return subj;
	}

	private void lisaaSulkutarkenteet(HashMap<String, Resource> tarkenteetResurssitMap) {
		HashSet<Statement> lisattavat = new HashSet<>();
		HashSet<Statement> poistettavat = new HashSet<>();
		for (String tarkenne:tarkenteetResurssitMap.keySet()) {
			Resource subj = tarkenteetResurssitMap.get(tarkenne);
			StmtIterator iter = this.koko.listStatements(subj, SKOS.prefLabel, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				if (stmt.getLanguage().equals("fi")) {
					String prefLabelString = ((Literal)stmt.getObject()).getLexicalForm();
					poistettavat.add(stmt);
					if (tarkenne.length() > 0) {
						String tarkenteeton = "";
						String tarkenteet = "";
						if (prefLabelString.contains("(")) {
							// yhdistetään sulkutarkenteita
							tarkenteeton = prefLabelString.substring(0, prefLabelString.indexOf("(")).trim();
							tarkenteet = prefLabelString.substring(prefLabelString.indexOf("(")).replace("(", "").replace(")", "").trim();
						} else tarkenteeton = prefLabelString;
						if (!prefLabelString.contains(tarkenne)) {
								if (tarkenteet.length() > 0) tarkenne = "(" + tarkenteet + ", " + tarkenne + ")";
								else tarkenne = "("+ tarkenne + ")";

								prefLabelString = tarkenteeton + " " + tarkenne;
						}

					}
					lisattavat.add(this.koko.createStatement(subj, SKOS.prefLabel, this.koko.createLiteral(prefLabelString, "fi")));
				}
			}
		}
		for (Statement s:poistettavat) this.koko.remove(s);
		for (Statement s:lisattavat) this.koko.add(s);
	}

	public HashMap<String, HashSet<Resource>> tuotaPrefLabelKokoSubjektitMap(String lang) {
		Resource deprekoitu = this.koko.createResource("http://purl.org/finnonto/schema/skosext#DeprecatedConcept");

		HashMap<String, HashSet<Resource>> prefLabelKokoSubjektitMap = new HashMap<String, HashSet<Resource>>();
		StmtIterator iter = this.koko.listStatements((Resource)null, SKOS.prefLabel, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getLanguage().equals(lang)) {
				// Ei oteta huomioon deprekoitujen käsitteiden prefLabeleita
				if (stmt.getSubject().hasProperty(RDF.type, deprekoitu))
					continue;
				Resource subj = stmt.getSubject();
				String labelString = ((Literal)stmt.getObject()).getLexicalForm();
				HashSet<Resource> subjektitSet = new HashSet<Resource>();
				if (prefLabelKokoSubjektitMap.containsKey(labelString)) {
					subjektitSet = prefLabelKokoSubjektitMap.get(labelString);
				}
				subjektitSet.add(subj);
				prefLabelKokoSubjektitMap.put(labelString, subjektitSet);
			}
		}
		return prefLabelKokoSubjektitMap;
	}
	/**
	 * Vaihtoehtoinen metodi joka valitsee keskeisen käsitteen kullekin käsiteryppäälle
	 *  @author joelitak
	 *  @version 0.3
	 *  @since 2019-03-07
	 */
	public void vaihtoehtoinenMuutaUritKokoUreiksi() {
		//testataan käsitteiden määrää 1.
		System.out.println("Kokossa käsitteitä: " + this.koko.listSubjects().toList().size());

		HashMap<Resource, HashSet<Resource>> ryhmaIndeksi = new HashMap<>();

		// Muodosta käsiterymiä jotka linkittyvät toisiinsa skos:exactMatchien avulla
		HashSet<Statement> kaikkiLinkitetytKasitteet = new HashSet<Statement>();

		StmtIterator iter1 = this.koko.listStatements(null, SKOS.exactMatch, (RDFNode)null);
		kaikkiLinkitetytKasitteet.addAll(iter1.toSet());

		System.out.println("Linkitettyjä käsitteitä oli " + kaikkiLinkitetytKasitteet.size());
		this.siivoaPoisTuplaVastaavuudet(kaikkiLinkitetytKasitteet);
		System.out.println("Siivouksen jälkeen linkitettyjä on " + kaikkiLinkitetytKasitteet.size());

		for (Statement linkki : kaikkiLinkitetytKasitteet) {

			Resource A = linkki.getSubject();
			Resource B = linkki.getResource();

			if ( ryhmaIndeksi.containsKey(A) &&
					ryhmaIndeksi.containsKey(B) &&
					ryhmaIndeksi.get(A) != ryhmaIndeksi.get(B) ) {

				//yhdistä ryhmät
				ryhmaIndeksi.get(A).addAll(ryhmaIndeksi.get(B));
				ryhmaIndeksi.remove(B);
				ryhmaIndeksi.put(B, ryhmaIndeksi.get(A));

			} else if ( ryhmaIndeksi.containsKey(A) ) {
				//lisää olemassaolevaan
				ryhmaIndeksi.get(A).add(B);
			} else if ( ryhmaIndeksi.containsKey(B) ) {
				//lisää olemassaolevaan
				ryhmaIndeksi.get(B).add(A);
			} else {
				//luo uusi ryhma
				HashSet<Resource> uusi = new HashSet<Resource>();
				uusi.add(A);
				uusi.add(B);
				ryhmaIndeksi.put(A, uusi);
				ryhmaIndeksi.put(B, uusi);
			}
		}
		
		//Lisää vielä kaikki skos:Conceptit että erilleen jäävät käsitteet tulevat kokoon mukaan
		StmtIterator iter3 = this.koko.listStatements(null, RDF.type, SKOS.Concept);

		while (iter3.hasNext()) {
			Resource subj = iter3.next().getSubject();

			if (!ryhmaIndeksi.containsKey(subj)) {
				HashSet<Resource> newSet = new HashSet<Resource>();
				newSet.add(subj);
				ryhmaIndeksi.put(subj, newSet);
			}		

		}

		//Jokaiselle käsiteryhmalle, listaa kaikki käsitteet ja valitse niistä pienin kokourivastaavuus vanhasta kokosta
		for (HashSet<Resource> ryhma : ryhmaIndeksi.values()) {

			Vector<Resource> ryhmanKokot = new Vector<Resource>();
			for (Resource r : ryhma) {
				Resource uusiKoko = this.ontoKokoResurssivastaavuudetMap.get(r);
				if (uusiKoko != null) {
					ryhmanKokot.add(uusiKoko);
				} 
			}
			//Jos kokourivastaavuutta ei löydy, luo uusi kokouri
			if (ryhmanKokot.size() == 0 || ryhmanKokot.get(0) == null) {
				Resource uusi = luoUusiKokoResurssi();
				ryhmanKokot.add(uusi);

				//Lisätään ontojen resursseille uusi timestamp ja kirjoitetaan vastaavuudet taulukkoon
				for (Resource r : ryhma) {
					this.urivastaavuusTimestampit.put(r, this.timestampNow);
				}
			} else {

				Collections.sort(ryhmanKokot, new ResourceComparator());
			}
			Resource kokoSubj = ryhmanKokot.get(0);


			for (Resource ontoSubj:ryhma) {
				//Miksi tama tehdaan kummallekin?
				this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.put(ontoSubj, kokoSubj);

				//tämä päätyy tiedostoon:
				if (this.kuuluuKokoUriTaulukkoon(ontoSubj)) {
					if (!this.ontoKokoResurssivastaavuudetMap.containsKey(ontoSubj))
						this.ontoKokoResurssivastaavuudetMap.put(ontoSubj, kokoSubj);
				}
			}
			for (Resource r : ryhma) {
				this.muutaKokoSubj(r, kokoSubj);
			}
			//Poista muut viittaukset kokoureihin ?
			for (int i=1 ; i<ryhmanKokot.size(); i++) {
				if (!kokoSubj.equals(ryhmanKokot.get(i)))
					this.koko.add(ryhmanKokot.get(i), DCTerms.isReplacedBy, kokoSubj);
			}
			//lisätään kokon käsitteelle aikaleimat
			this.haeAikaleimat(ryhma, kokoSubj);

		}
		//valitaan lopuksi jokaiselle uudelle koko-käsitteelle yso-pohjainen prefLabel niissä tapauksissa joissa prefLabelit ovat tulleet ysosta ja erikois-ontoista
		Property ysoPref = this.koko.createProperty("http://purl.org/finnonto/schema/skosext#ysoPrefLabel");
		Property candidateLabel = this.koko.createProperty(skosextNs + "candidateLabel");
		ArrayList<Statement> lisattavat = new ArrayList<Statement>();
		ArrayList<Statement> poistettavat = new ArrayList<Statement>();
		StmtIterator kokoConIter = this.koko.listStatements(null, RDF.type, SKOS.Concept);

		while (kokoConIter.hasNext()) {
			Statement s = kokoConIter.next();
			Resource kokoCon = s.getSubject();
			if (kokoCon.hasProperty(ysoPref)) {
				poistettavat.addAll(kokoCon.listProperties(ysoPref).toSet());
				poistettavat.addAll(kokoCon.listProperties(SKOS.prefLabel).toSet());
				poistettavat.addAll(kokoCon.listProperties(candidateLabel).toSet());
				StmtIterator kokoConPrefLabelIter = kokoCon.listProperties(SKOS.prefLabel);
				while (kokoConPrefLabelIter.hasNext()) {
					Statement prefStmt = kokoConPrefLabelIter.next();
					lisattavat.add(this.koko.createStatement(prefStmt.getSubject(), SKOS.altLabel, prefStmt.getObject()));
				}
				StmtIterator kokoConYsoPrefLabelIter = kokoCon.listProperties(ysoPref);
				while (kokoConYsoPrefLabelIter.hasNext()) {
					Statement ysoPrefStmt = kokoConYsoPrefLabelIter.next();
					lisattavat.add(this.koko.createStatement(ysoPrefStmt.getSubject(), SKOS.prefLabel, ysoPrefStmt.getObject()));
				}
			} else {
				// Käsitteellä on useampi prefLabel mutta ei selkeää yso-labelia
				List<Statement> prefLabels = kokoCon.listProperties(SKOS.prefLabel).toList();
				if (prefLabels.size() > 1) {
					Statement lyhin = prefLabels.get(0);
					// valitse lyhyempi prefLabeliksi, pitempi altLabeliksi
					for (Statement duplicatePrfStmt : prefLabels) {
						if (duplicatePrfStmt.getLiteral().getLexicalForm().length() < lyhin.getLiteral().getLexicalForm().length())
							lyhin = duplicatePrfStmt;
					}
					for (Statement duplicatePreStatement : prefLabels) {
						if (duplicatePreStatement.equals(lyhin))
							lisattavat.add(lyhin);
						else {
							poistettavat.add(duplicatePreStatement);
							lisattavat.add(this.koko.createStatement(duplicatePreStatement.getSubject(), SKOS.altLabel, duplicatePreStatement.getObject()));
						}
					}
				}
			}
		}
		this.koko.remove(poistettavat);
		this.koko.add(lisattavat);
	}
	/*
	 * KOKO-URIttamisen jälkeen siivotaan kokosta pois dct:isreplacedby erikoisontologiasubjektit
	 */
	private void siivoaLopuksiOntoSubjektit(String replacedbyPolku) {
		Model replacedByt = ModelFactory.createDefaultModel();
		replacedByt.setNsPrefix("koko", kokoNs);
		replacedByt.setNsPrefix("dct", DCTerms.getURI());
		Model pois = ModelFactory.createDefaultModel();
		StmtIterator subjects = this.koko.listStatements(null, DCTerms.isReplacedBy, (RDFNode)null);
		while (subjects.hasNext()) {
			Statement s = subjects.next();
			Resource subject = s.getSubject();

			StmtIterator temp = subject.listProperties();
			if (temp.toSet().size() == 1) {
				if (!subject.getURI().contains("koko")) {//poistetaan erikoisonto -> koko -viittaukset, nämä ylläpidetään urivastaavuuslistassa
					pois.add(s);
					System.out.println("Tripleä " + s + " ei otettu mukaan KOKOon");
				} else { //siirretään omaan tiedostoonsa
					replacedByt.add(s);
				}
			}
			temp.close();
		}
		subjects.close();

		this.koko.remove(pois);
		this.koko.remove(replacedByt);

		try {
			replacedByt.write(new FileOutputStream(replacedbyPolku), "TURTLE");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void haeAikaleimat(HashSet<Resource> ryhma, Resource kokoRes) {
		ArrayList<Date> created = new ArrayList<Date>();
		ArrayList<Date> modified = new ArrayList<Date>();

		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		SimpleDateFormat vaihtoehtoFormat = new SimpleDateFormat("yyyyMMdd");

		for (Resource res : ryhma) {
			StmtIterator i = this.aikaLeimat.listStatements(res, null, (RDFNode)null);
			while (i.hasNext()) {
				Statement s = i.next();
				if (s.getPredicate().equals(DCTerms.created)) {
					try {
						created.add(  format.parse(s.getLiteral().getString())  );
					} catch (ParseException e1) {
						try {
							created.add(  vaihtoehtoFormat.parse(s.getLiteral().getString())  );
						} catch (ParseException e2) {
							System.out.println("Ongelma: Aikaleimaa ei voitu jäsentää " + s.toString());
						}
					}
				}
				else if (s.getPredicate().equals(DCTerms.modified)) {
					try {
						modified.add(  format.parse(s.getLiteral().getString()) );
					} catch (ParseException e1) {
						try {
							modified.add(  vaihtoehtoFormat.parse(s.getLiteral().getString())  );
						} catch (ParseException e2) {
							System.out.println("Ongelma: Aikaleimaa ei voitu jäsentää " + s.toString());
						}
					}
				}
			}
		}
		Collections.sort(created); //vanhin luomispäivä
		Collections.sort(modified, Collections.reverseOrder()); //uusin muokkauspäivä

		if (!created.isEmpty()) {
			String dateString = format.format(created.get(0));
			this.koko.add(kokoRes, DCTerms.created, this.koko.createTypedLiteral(dateString, XSDDatatype.XSDdate));
		}
		if (!modified.isEmpty()) {
			String dateString = format.format(modified.get(0));
			this.koko.add(kokoRes, DCTerms.modified, this.koko.createTypedLiteral(dateString, XSDDatatype.XSDdate));
		}

	}

	private void muutaKokoSubj(Resource vanhaSubj, Resource uusiSubj) {
		Property ysoPrefLabel = this.koko.createProperty("http://purl.org/finnonto/schema/skosext#ysoPrefLabel");
		Resource ysoCon = this.koko.createProperty("http://www.yso.fi/onto/yso-meta/Concept");
		Resource deprekoitu = this.koko.createResource("http://purl.org/finnonto/schema/skosext#DeprecatedConcept");
		HashSet<Statement> poistettavat = new HashSet<Statement>();
		HashSet<Statement> lisattavat = new HashSet<Statement>();
		StmtIterator iter = this.koko.listStatements(vanhaSubj, (Property)null, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			poistettavat.add(stmt);
			//Lisätään huomio prefLabeleista - jos kyseessä on yso-käsite jota ei ole deprekoitu, tallennetaan prefLabel ysoPrefLabelina myöhempää erottelua varten
			if (stmt.getPredicate().equals(SKOS.prefLabel) && stmt.getSubject().hasProperty(RDF.type, ysoCon) && !stmt.getSubject().hasProperty(RDF.type, deprekoitu)) {
				lisattavat.add(this.koko.createStatement(uusiSubj, ysoPrefLabel, stmt.getObject()));
			} else {
				lisattavat.add(this.koko.createStatement(uusiSubj, stmt.getPredicate(), stmt.getObject()));
			}
		}
		for (Statement s:poistettavat) this.koko.remove(s);
		for (Statement s:lisattavat) this.koko.add(s);
	}

	public void parsiErikoisontologioidenPolutVektoriin(String ontologioidenPolutJaTyypitTxt) {
		this.ontologioidenTyypitPolutMap = new HashMap<Resource, String>();
		Vector<String> polkuVektori = new Vector<String>();
		try {
			BufferedReader br = new BufferedReader( new InputStreamReader( new FileInputStream(ontologioidenPolutJaTyypitTxt), "UTF8" ) );

			String rivi = br.readLine().trim();
			while (rivi != null) {
				rivi = rivi.trim();
				if (!rivi.startsWith("#")) { 
					String[] uriTaulukko = rivi.split(" ");
					polkuVektori.add(uriTaulukko[1].trim());
					this.ontologioidenTyypitPolutMap.put(this.koko.createResource(uriTaulukko[0].trim()), uriTaulukko[1].trim());
				}
				rivi = br.readLine();
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void korjaaLopuksiObjectit() {
		System.out.println("Korjataan KOKOn objektit.");		
		HashSet<Statement> poistettavat = new HashSet<Statement>();
		HashSet<Statement> lisattavat = new HashSet<Statement>();
		StmtIterator iter = this.koko.listStatements();
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (!(stmt.getPredicate().equals(SKOS.exactMatch))) {
				RDFNode obj = stmt.getObject();
				if (obj.isURIResource()) {
					if (this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.containsKey(obj)) {
						Resource uusiObj = this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.get(obj);
						poistettavat.add(stmt);
						lisattavat.add(this.koko.createStatement(stmt.getSubject(), stmt.getPredicate(), uusiObj));
					}
				}
			}
		}
		for (Statement stmt:poistettavat) this.koko.remove(stmt);
		for (Statement stmt:lisattavat) this.koko.add(stmt);

		//Siivotaan lopuksi hierarkiasta erikoisonto- ja ysourit joihin on saatettu viitata exactMatcheilla
		HashSet<Statement> korvattavatHierarkiset = new HashSet<Statement>();
		StmtIterator broaderIter = this.koko.listStatements(null, SKOS.broader, (RDFNode)null);
		while (broaderIter.hasNext()) {
			Statement s = broaderIter.next();
			if (!s.getResource().getNameSpace().endsWith("/koko/")) {
				korvattavatHierarkiset.add(s);
			}
		}
		StmtIterator narrowerIter = this.koko.listStatements(null, SKOS.narrower, (RDFNode)null);
		while (narrowerIter.hasNext()) {
			Statement s = narrowerIter.next();
			if (!s.getResource().getNameSpace().endsWith("/koko/")) {
				korvattavatHierarkiset.add(s);
			}
		}

		for (Statement s : korvattavatHierarkiset) {
			Resource korvaava = this.ontoKokoResurssivastaavuudetMap.get(s.getResource());
			this.koko.remove(s);
			if (korvaava == null) {
				System.out.println("Käsitteelle " + s.getResource() + " ei löytynyt vastaavaa koko-uria. Poistetaan viittaus uriin.");
			} else {
				this.koko.add(s.getSubject(), s.getPredicate(), korvaava);
			}
		}

		//testataan käsitteiden määrää 2.
		System.out.println("Kokossa käsitteitä urittamisen jälkeen: " + this.koko.listSubjects().toList().size());

	}

	public void tulostaPrefLabelMuutoksetEdelliseenVerrattuna(Model aiempiKoko, String lang) {
		System.out.println("KOKOn prefLabel muutokset edelliseen versioon verrattuna kielellä " + lang + ":");
		Resource deprekoitu = this.koko.createResource("http://purl.org/finnonto/schema/skosext#DeprecatedConcept");
		int i = 0;

		HashMap<Resource, String> nykyKokonPrefLabelitMap = new HashMap<Resource, String>();
		StmtIterator iter = this.koko.listStatements((Resource)null, SKOS.prefLabel, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getLanguage().equals(lang)) {
				//Ei oteta huomioon deprekoitujen käsitteiden muutoksia
				if (stmt.getSubject().hasProperty(RDF.type, deprekoitu))
					continue;
				String fiLabelString = ((Literal)stmt.getObject()).getLexicalForm();
				nykyKokonPrefLabelitMap.put(stmt.getSubject(), fiLabelString);
			}
		}

		iter = aiempiKoko.listStatements((Resource)null, SKOS.prefLabel, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getLanguage().equals(lang)) {
				String fiLabelString = ((Literal)stmt.getObject()).getLexicalForm();
				if (nykyKokonPrefLabelitMap.containsKey(stmt.getSubject())) {
					String nykyFiLabelString = nykyKokonPrefLabelitMap.get(stmt.getSubject());
					String nykyFiLabelStringIlmanSulkutarkenteita = nykyFiLabelString;
					if (nykyFiLabelString.contains("(")) nykyFiLabelStringIlmanSulkutarkenteita = nykyFiLabelString.substring(0, nykyFiLabelString.indexOf("(")).trim();
					String fiLabelStringIlmanSulkutarkenteita = fiLabelString;
					if (fiLabelString.contains("(")) fiLabelStringIlmanSulkutarkenteita = fiLabelString.substring(0, fiLabelString.indexOf("(")).trim();
					if (!(nykyFiLabelStringIlmanSulkutarkenteita.equals(fiLabelStringIlmanSulkutarkenteita))) {
						i++;
						System.out.println(i + ". " + stmt.getSubject().getURI() + " -- " + fiLabelString + " => " + nykyFiLabelString);
					}
				}
			}
		}
	}

	public void tulostaMuutoksetEdelliseenVerrattuna(String aiemmanKokonpolku) {
		System.out.println("Tulostetaan prefLabel-muutokset edelliseen KOKOon verrattuna.");		
		int i = 0;
		Model aiempiKoko = JenaHelpers.lueMalliModeliksi(aiemmanKokonpolku);
		Resource deprekoitu = this.koko.createResource("http://purl.org/finnonto/schema/skosext#DeprecatedConcept");
		aiempiKoko = JenaHelpers.muunnaKielikoodittomatLabelitSuomenkielisiksi(aiempiKoko, SKOS.prefLabel);

		HashMap<Resource, String> nykyKokonPrefLabelitMap = new HashMap<Resource, String>();
		StmtIterator iter = this.koko.listStatements((Resource)null, SKOS.prefLabel, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getLanguage().equals("fi")) {
				//Ei huomioida muutoksia deprekoiduissa käsitteissä
				if (stmt.getSubject().hasProperty(RDF.type, deprekoitu))
					continue;
				String fiLabelString = ((Literal)stmt.getObject()).getLexicalForm();
				nykyKokonPrefLabelitMap.put(stmt.getSubject(), fiLabelString);
			}
		}

		this.tulostaPrefLabelMuutoksetEdelliseenVerrattuna(aiempiKoko, "fi");
		this.tulostaPrefLabelMuutoksetEdelliseenVerrattuna(aiempiKoko, "sv");

		HashSet<Resource> uudenKokonResurssit = new HashSet<Resource>();
		ResIterator resIter = this.koko.listSubjectsWithProperty(RDF.type, SKOS.Concept);
		while (resIter.hasNext()) {
			uudenKokonResurssit.add(resIter.nextResource());
		}
		resIter = aiempiKoko.listSubjectsWithProperty(RDF.type, SKOS.Concept);
		while (resIter.hasNext()) {
			uudenKokonResurssit.remove(resIter.nextResource());
		}
		//Poistetaan deprekoidut laskuista
		resIter = this.koko.listResourcesWithProperty(RDF.type, deprekoitu);
		while (resIter.hasNext()) {
			uudenKokonResurssit.remove(resIter.nextResource());
		}

		System.out.println("Uudessa KOKOssa on " + uudenKokonResurssit.size() + " uutta käsitettä.");
		i = 0; 
		for (Resource uusi:uudenKokonResurssit) {
			i++;
			System.out.println(i + ". " + uusi.getURI() + " = " + nykyKokonPrefLabelitMap.get(uusi));
		}
	}

	public void lisaaExactMatchitAiemmassaKokossaOlleisiin(String aiemmanKokonpolku, String aiemmanKokonReplacedby) {
		System.out.println("Lisätään linkit aiemmassa KOKOssa olleisiin käsitteisiin.");
		int i = 0;
		Model aiempiKoko = JenaHelpers.lueMalliModeliksi(aiemmanKokonpolku);
		aiempiKoko.add(ModelFactory.createDefaultModel().read(aiemmanKokonReplacedby));
		aiempiKoko = JenaHelpers.muunnaKielikoodittomatLabelitSuomenkielisiksi(aiempiKoko, SKOS.prefLabel);
		HashSet<Resource> nykyKokonResurssit = new HashSet<Resource>();
		ResIterator resIter = this.koko.listResourcesWithProperty(RDF.type, SKOS.Concept);
		while (resIter.hasNext()) {
			Resource subj = resIter.nextResource();
			nykyKokonResurssit.add(subj);
		}

		HashSet<Resource> aiemmassaKokossaOlleetMuttaNykyKokostaPuuttuvatSkosConceptit = new HashSet<Resource>();
		resIter = aiempiKoko.listResourcesWithProperty(RDF.type, SKOS.Concept);
		while (resIter.hasNext()) {
			Resource subj = resIter.nextResource();
			if (!nykyKokonResurssit.contains(subj)) {
				aiemmassaKokossaOlleetMuttaNykyKokostaPuuttuvatSkosConceptit.add(subj);
			}
		}

		//System.out.println(aiemmassaKokossaOlleetMuttaNykyKokostaPuuttuvatResurssit.size());
		HashMap<Resource, String> aiemmanKokonNykyKokostaPuuttuvienFiLabelitMap = this.haeTietynTyyppistenResurssienLabelitMappiin(aiempiKoko, SKOS.prefLabel, aiemmassaKokossaOlleetMuttaNykyKokostaPuuttuvatSkosConceptit, "fi");
		//System.out.println(aiemmanKokonNykyKokostaPuuttuvienFiLabelitMap.size());

		for (Resource subj:aiemmassaKokossaOlleetMuttaNykyKokostaPuuttuvatSkosConceptit) {
			boolean loytyiVastine = false;
			HashSet<Resource> subjinAiemmassaKokossaOlevatExactMatchit = new HashSet<Resource>();
			StmtIterator iter = aiempiKoko.listStatements(subj, SKOS.exactMatch, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				if (eiToivottuViittaus(stmt))
					continue;
				Resource obj = (Resource)(stmt.getObject());
				subjinAiemmassaKokossaOlevatExactMatchit.add(obj);
			}
			iter = aiempiKoko.listStatements(subj, DCTerms.isReplacedBy, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				Resource obj = (Resource)(stmt.getObject());
				subjinAiemmassaKokossaOlevatExactMatchit.add(obj);
			}
			for (Resource obj:subjinAiemmassaKokossaOlevatExactMatchit) {
				if (nykyKokonResurssit.contains(obj)) {
					loytyiVastine = true;
					this.koko.add(subj, DCTerms.isReplacedBy, obj);
				}
			}
			if (!loytyiVastine) {
				for (Resource obj:subjinAiemmassaKokossaOlevatExactMatchit) {
					if (this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.containsKey(obj)) {
						loytyiVastine = true;
						this.koko.add(subj, DCTerms.isReplacedBy, this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.get(obj));
					}
				}
			}
			if (!loytyiVastine) {
				if (aiemmanKokonNykyKokostaPuuttuvienFiLabelitMap.containsKey(subj)) {
					String labelString = aiemmanKokonNykyKokostaPuuttuvienFiLabelitMap.get(subj);
					if (this.kokoFiLabelitResurssitMap.containsKey(labelString)) {
						loytyiVastine = true;
						this.koko.add(subj, DCTerms.isReplacedBy, this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.get(this.kokoFiLabelitResurssitMap.get(labelString)));
					}
				}
			}
			if (!loytyiVastine) {
				i++;
				if (aiemmanKokonNykyKokostaPuuttuvienFiLabelitMap.containsKey(subj)) {
					System.out.println(i + ". ongelma: Edellisessä KOKOssa oli käsite, jolle ei loytynyt vastinetta uuteen KOKOon: " + aiemmanKokonNykyKokostaPuuttuvienFiLabelitMap.get(subj) + " -- " + subj.getURI());
				} else {
					System.out.println(i + ". ongelma: Edellisessä KOKOssa oli käsite, jolle ei loytynyt vastinetta uuteen KOKOon: " + subj.getURI());
				}
			}
		}
		// Poistetaan itseensa osoittavat replacedByt, seka ne, jotka tulisivat nyky-KOKOssa ihan varsinaisina kasitteina olevista
		HashSet<Statement> poistettavat = new HashSet<Statement>();
		StmtIterator iter = this.koko.listStatements((Resource)null, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getSubject().equals(stmt.getObject())) poistettavat.add(stmt);
			else {
				StmtIterator iter2 = this.koko.listStatements(stmt.getSubject(), RDF.type, (RDFNode)null);
				if (iter2.hasNext()) poistettavat.add(stmt);
			}
		}
		for (Statement s:poistettavat) this.koko.remove(s);

		HashSet<Statement> lisattavat = new HashSet<Statement>();
		HashSet<Resource> nykyKokonAivanKaikkiResurssitSet = new HashSet<Resource>();
		iter = this.koko.listStatements();
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			nykyKokonAivanKaikkiResurssitSet.add(stmt.getSubject());
		}
		//HashSet<Resource> aiemmanKokonReplacedBytJotkaPuuttuvatUudesta = new HashSet<Resource>();
		iter = aiempiKoko.listStatements((Resource)null, DCTerms.isReplacedBy, (RDFNode)null);
		i = 0;
		int j = 0;
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (!nykyKokonAivanKaikkiResurssitSet.contains(stmt.getSubject())) {
				Resource obj = (Resource)stmt.getObject();
				if (nykyKokonAivanKaikkiResurssitSet.contains(obj)) {
					lisattavat.add(stmt);
					j++;
				} else {
					i++;
					System.out.println(i + ". vanhassa KOKOssa oli replaced-by-triple, jota ei voitu toisintaa uuteen KOKOon: " + stmt.toString());
				}
			}
		}
		for (Statement s:lisattavat) this.koko.add(s);
		System.out.println("Lisättiin " + j + " replacedByta aiemmasta KOKOsta.");
		//JenaHelpers.testaaMallinLabelienKielet(aiempiKoko, skosPrefLabel);
	}

	public void tarkistaEtteiKorvattuihinMeneSuhteitaJaPuraMahdollisetKorvaavuusKetjut() {
		System.out.println("Tarkistetaan ettei korvattuihin mene suhteita ja puretaan mahdolliset korvaavuusketjut.");
		HashSet<Statement> lisattavat = new HashSet<Statement>();
		HashSet<Statement> poistettavat = new HashSet<Statement>();

		HashMap<Resource, Resource> korvaavuusMap = new HashMap<Resource, Resource>();
		StmtIterator iter = this.koko.listStatements((Resource)null, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			HashSet<Resource> korvattavat = new HashSet<Resource>();
			Resource korvattava = stmt.getSubject();
			Resource korvaava = (Resource)(stmt.getObject());

			poistettavat.add(stmt);

			/* Ei otetakaan huomioon korvaavuussuhteita kahden koko-käsitteen välillä,
			 * jos korvattava koko-käsite on korvattu useammalla koko-käsitteellä
			 */

			if (this.koko.listStatements(korvattava, DCTerms.isReplacedBy, (RDFNode)null).toSet().size() > 1) {
				continue;
			}

			korvattavat.add(korvattava);
			korvaavuusMap.put(korvattava, korvaava);
			boolean jatka = true;
			while (jatka) {
				StmtIterator iter2 = this.koko.listStatements(korvaava, DCTerms.isReplacedBy, (RDFNode)null);
				if (iter2.hasNext()) {
					Statement stmt2 = iter2.nextStatement();
					//lisatty tarkistus ikuisten looppien ehkäisemiseksi
					if (poistettavat.contains(stmt2))
						jatka = false;
					poistettavat.add(stmt2);
					korvattavat.add(korvaava);
					korvaava = (Resource)(stmt2.getObject());
				} else jatka = false;
			}
			for (Resource r:korvattavat) {
				korvaavuusMap.put(r, korvaava);
				lisattavat.add(this.koko.createStatement(r, DCTerms.isReplacedBy, korvaava));
			}

		}

		for (Statement s:poistettavat) this.koko.remove(s);
		for (Statement s:lisattavat) this.koko.add(s);

		System.out.println("poistettiin:");
		for (Statement s:poistettavat) this.koko.remove(s);
		System.out.println("lisättiin:");
		for (Statement s:lisattavat) this.koko.add(s);


		lisattavat = new HashSet<Statement>();
		poistettavat = new HashSet<Statement>();
		iter = this.koko.listStatements();
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			Resource obj = null;
			if (stmt.getObject().isURIResource()) obj = (Resource)stmt.getObject(); 
			if (korvaavuusMap.containsKey(obj)) {
				poistettavat.add(stmt);
				lisattavat.add(this.koko.createStatement(stmt.getSubject(), stmt.getPredicate(), korvaavuusMap.get(obj)));
			}
		}
		for (Statement s:poistettavat) this.koko.remove(s);
		for (Statement s:lisattavat) this.koko.add(s);

		System.out.println("triplet korvattuihin muutettu");
		System.out.println("poistettiin:");
		for (Statement s:poistettavat) this.koko.remove(s);
		System.out.println("lisättiin:");
		for (Statement s:lisattavat) this.koko.add(s);

		System.out.println("Tarkistettu.");
	}

	public void lueMustaLista(String mustanListanPolku) {
		this.mustaLista = new HashSet<Resource>();
		try {
			BufferedReader br = new BufferedReader( new InputStreamReader( new FileInputStream(mustanListanPolku), "UTF8" ) );

			String rivi = br.readLine().trim();
			while (rivi != null) {
				rivi = rivi.trim();
				this.mustaLista.add(this.koko.createResource(rivi));
				rivi = br.readLine();
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void kirjoitaUudetUriVastaavuudet(String tiedostonPolku) {
		try {
			FileWriter fstream = new FileWriter(tiedostonPolku);
			BufferedWriter out = new BufferedWriter(fstream);
			for (Resource vanhaUriRes:this.ontoKokoResurssivastaavuudetMap.keySet()) {
				String vanhaUriString = vanhaUriRes.getURI();
				String kokoUriString = this.ontoKokoResurssivastaavuudetMap.get(vanhaUriRes).getURI();
				String timestampString = (this.urivastaavuusTimestampit.containsKey(vanhaUriRes))
						? this.urivastaavuusTimestampit.get(vanhaUriRes) : "";
				if (timestampString.length() > 0) timestampString = " " + timestampString;
				out.write(vanhaUriString + " " + kokoUriString + timestampString + "\n");
			}
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Uudet KOKO-URI-vastaavuudet kirjoitettu tiedostoon " + tiedostonPolku);
	}

	public void kokoa(String ysonPolku, String erikoisontologiaTxtnPolku, String edellisenKokonPolku, String uusienUrivastaavuuksienPolku, String mustanListanPolku, String replacedbyPolku) {
		this.lueMustaLista(mustanListanPolku);
		this.lueYso(ysonPolku);
		this.parsiErikoisontologioidenPolutVektoriin(erikoisontologiaTxtnPolku);
		for (Resource ontonTyyppi:this.ontologioidenTyypitPolutMap.keySet()) {
			String polku = this.ontologioidenTyypitPolutMap.get(ontonTyyppi);
			this.lueOnto(polku, ontonTyyppi);
		}
		this.korjaaYlataso();
		this.muutaCandidateLabelitPrefJaAltLabeleiksi();
		this.romautaFiPrefLabelienJaVanhempienPerusteella();
		this.vaihtoehtoinenMuutaUritKokoUreiksi();
		this.siivoaLopuksiOntoSubjektit(replacedbyPolku);
		this.korjaaLopuksiObjectit();
		this.korjaaLopuksiLabelit();
		this.lisaaExactMatchitAiemmassaKokossaOlleisiin(edellisenKokonPolku, replacedbyPolku);
		this.tarkistaEtteiKorvattuihinMeneSuhteitaJaPuraMahdollisetKorvaavuusKetjut();
		this.tulostaMuutoksetEdelliseenVerrattuna(edellisenKokonPolku);
		this.kirjoitaUudetUriVastaavuudet(uusienUrivastaavuuksienPolku);
		
		this.korjaaHierarkia(edellisenKokonPolku);
		//this.korjaaSulkutarkenteet();
				
		System.out.println("Labelin perusteella romautettiin " + this.romautetut + ".");
	}

	private void korjaaLopuksiLabelit() {
		System.out.println("Korjataan KOKOn labelit.");
		ArrayList<Statement> poistettavat = new ArrayList<Statement>();
		StmtIterator iter = this.koko.listStatements(null, RDF.type, SKOS.Concept);

		while (iter.hasNext()) {
			Statement stm = iter.next();
			Resource res = stm.getSubject();
			for (String lang : this.kielet) {

				// Jos käsitteellä on sama label preffinä ja jonain muuna, poistetaan muut
				Statement prefSt = res.getProperty(SKOS.prefLabel, lang);
				if (prefSt != null) {
					Literal pref = res.getProperty(SKOS.prefLabel, lang).getLiteral();

					StmtIterator prefCheckIter = this.koko.listStatements(res, null, pref);
					while (prefCheckIter.hasNext()) {
						Statement prefCheckStmt = prefCheckIter.next();
						if (prefCheckStmt.getPredicate().equals(SKOS.altLabel) ||
								prefCheckStmt.getPredicate().equals(SKOS.hiddenLabel)) {
							poistettavat.add(prefCheckStmt);
						}

					} prefCheckIter.close();
				}
				// Jos käsitteellä on sama label hiddeninä ja altina, poistetaan hidden
				StmtIterator hiddenIter = res.listProperties(SKOS.hiddenLabel, lang);
				while (hiddenIter.hasNext()) {
					Statement hiddenStmt = hiddenIter.next();
					Literal hidden = hiddenStmt.getLiteral();
					if (res.hasProperty(SKOS.altLabel, hidden)) {
						poistettavat.add(hiddenStmt);
					}
				} hiddenIter.close();

			}
		} iter.close();
		System.out.println("KOKOsta poistettiin " + poistettavat.size() + " kpl ylimääräisiä labeleita.");
		this.koko.remove(poistettavat);
	}

	/*
	 * Varmistetaan erikoisontologioiden sisäänlukemisen jälkeen,
	 * ettei mikään niistä sisällä vanhanmallisen YSO-hierarkian
	 * lauseita jotka sotkisivat nyky-YSOn ylätason hierarkiaa
	 */
	private void korjaaYlataso() {
		HashSet<Resource> sallittuYlataso = new HashSet<Resource>();
		sallittuYlataso.add(this.koko.createResource("http://www.yso.fi/onto/yso/p4762")); //oliot
		sallittuYlataso.add(this.koko.createResource("http://www.yso.fi/onto/yso/p8691")); //ominaisuudet
		sallittuYlataso.add(this.koko.createResource("http://www.yso.fi/onto/yso/p15238")); //tapahtumat ja toiminta

		for (Resource r : sallittuYlataso) {
			this.koko.removeAll(r, SKOS.broader, (RDFNode)null);
		}

	}

	public void kokoaJaTuotaDebugKoko(String ysonPolku, String erikoisontologiaTxtnPolku, String edellisenKokonPolku, String uusienUrivastaavuuksienPolku, String mustanListanPolku, String replacedbyPolku, String debugKokonPolku) {
		this.lueMustaLista(mustanListanPolku);
		this.lueYso(ysonPolku);
		this.parsiErikoisontologioidenPolutVektoriin(erikoisontologiaTxtnPolku);
		for (Resource ontonTyyppi:this.ontologioidenTyypitPolutMap.keySet()) {
			String polku = this.ontologioidenTyypitPolutMap.get(ontonTyyppi);
			this.lueOnto(polku, ontonTyyppi);
		}
		this.kirjoitaKoko(debugKokonPolku);
		this.muutaCandidateLabelitPrefJaAltLabeleiksi();
		this.romautaFiPrefLabelienJaVanhempienPerusteella();
		//this.kirjoitaKoko(debugKokonPolku);
		this.vaihtoehtoinenMuutaUritKokoUreiksi();
		this.siivoaLopuksiOntoSubjektit(replacedbyPolku);
		this.korjaaLopuksiObjectit();
		this.lisaaExactMatchitAiemmassaKokossaOlleisiin(edellisenKokonPolku, replacedbyPolku);
		this.tarkistaEtteiKorvattuihinMeneSuhteitaJaPuraMahdollisetKorvaavuusKetjut();
		this.tulostaMuutoksetEdelliseenVerrattuna(edellisenKokonPolku);
		this.kirjoitaUudetUriVastaavuudet(uusienUrivastaavuuksienPolku);
		
		this.korjaaHierarkia(edellisenKokonPolku);

		System.out.println("Labelin perusteella romautettiin " + this.romautetut + ".");
	}

	/**
	 * Huolehtii siitä, että Kokon ylätason hierarkiaan ei tipu käsitteitä jotka eivät ripustu minkään toisen käsitteen alle.
	 * Näitä käsitteitä kerätään kokon "Hierarkian ulkopuoliset"@fi -käsitteen alle.
	 *
	 */
	private void korjaaHierarkia(String edellisenKokonPolku) {
		Resource ulkopuoliset;
		Model aiempiKoko = JenaHelpers.lueMalliModeliksi(edellisenKokonPolku);

		Literal ulkopNimi = this.koko.createLiteral("hierarkian ulkopuoliset", "fi");

		Resource deprekoitu = this.koko.createResource("http://purl.org/finnonto/schema/skosext#DeprecatedConcept");

		StmtIterator ulkopuolisenNimi = aiempiKoko.listStatements(null, SKOS.prefLabel, ulkopNimi);
		if ( ulkopuolisenNimi.hasNext() ) {
			ulkopuoliset = ulkopuolisenNimi.next().getSubject();
			this.koko.add(ulkopuoliset.listProperties(SKOS.prefLabel));
			this.koko.add(ulkopuoliset.listProperties(SKOS.definition));
			this.koko.add(ulkopuoliset.listProperties(RDF.type));
			this.koko.add(ulkopuoliset, SKOS.topConceptOf, this.koko.createResource(kokoNs));

		}
		else  {
			ulkopuoliset = this.luoUusiKokoResurssi();
			this.ontoKokoResurssivastaavuudetMap.put(ulkopuoliset, ulkopuoliset);
			ulkopuoliset.addProperty(SKOS.prefLabel, ulkopNimi);
			ulkopuoliset.addProperty(SKOS.definition, "Käsitteet joille ei löytynyt yläkäsitettä päätyvät tänne.", "fi");
			ulkopuoliset.addProperty(RDF.type, SKOS.Concept);
			ulkopuoliset.addProperty(SKOS.topConceptOf, this.koko.createResource(kokoNs));
		}

		Set<Resource> jonkunLapset = this.koko.listResourcesWithProperty(SKOS.broader).toSet();
		Set<Resource> ylatasonResurssit = this.koko.listResourcesWithProperty(RDF.type, SKOS.Concept ).toSet();
		ylatasonResurssit.removeAll(jonkunLapset);
		
		HashSet<Resource> sallittuYlataso = new HashSet<Resource>();
		sallittuYlataso.add(this.koko.createResource("http://www.yso.fi/onto/koko/p37038")); //oliot
		sallittuYlataso.add(this.koko.createResource("http://www.yso.fi/onto/koko/p34034")); //ominaisuudet
		sallittuYlataso.add(this.koko.createResource("http://www.yso.fi/onto/koko/p35417")); //tapahtumat ja toiminta
		sallittuYlataso.add(ulkopuoliset); //hierarkian ulkopuoliset

		ArrayList<Statement> poistot = new ArrayList<Statement>();
		ArrayList<Statement> lisat = new ArrayList<Statement>();
		
		for (Resource r : ylatasonResurssit) {

			// Lisaksi huolehditaan ettei deprekoidut käsitteet päädy hierarkian ulkopuolisiin
			if (!sallittuYlataso.contains(r) && !r.hasProperty(RDF.type, deprekoitu)) {
				r.addProperty(SKOS.broader, ulkopuoliset);

				/*  Toivottuna korjauksena muokataan hierarkian ulkopuolisten käsitteiden prefLabeleita
				 *  siten että niiden sulkutarkenteeksi merkitään lähtösanaston lyhenne
				 */

				StmtIterator prefLabelIter = r.listProperties(SKOS.prefLabel);
				while (prefLabelIter.hasNext()) {
					Statement s = prefLabelIter.next();
					String lang = s.getLanguage();
					String literal = s.getLiteral().getLexicalForm();
					String sulkuTarkenteetonLiteral = literal;
					if (literal.contains("("))
						sulkuTarkenteetonLiteral = literal.substring(0, literal.lastIndexOf("(")).trim();

					String sanastoTunnus = "";
					StmtIterator tunnusIter = r.listProperties(RDF.type);
					while (tunnusIter.hasNext()) {
						Statement tunnusSt = tunnusIter.next();
						String typeUri = tunnusSt.getResource().getURI();
						if (typeUri.endsWith("-meta/Concept")) {
							sanastoTunnus = typeUri.substring(0, typeUri.indexOf("-meta/Concept"));
							sanastoTunnus = sanastoTunnus.substring(sanastoTunnus.lastIndexOf("/")+1).toUpperCase();
							break;
						}

					}
					Literal newLiteral = this.koko.createLiteral(sulkuTarkenteetonLiteral + " (" + sanastoTunnus + ")", lang);
					lisat.add(this.koko.createStatement(r, SKOS.prefLabel, newLiteral));
					poistot.add(s);
				}
			}
		}

		//poistetaan hierarkian ulkopuolisilta sulkutarkenteet
		this.koko.remove(poistot);

		//lisätään hierarkian ulkopuolisille sulkutarkenteeksi lähtösanaston tunnus
		this.koko.add(lisat);

		//varataan lopuksi hierarkian ulkopuolisten resurssiuri kokon urivastaavuustiedostoon
		this.ontoKokoResurssivastaavuudetMap.put(ulkopuoliset, ulkopuoliset);
	}

	public void kirjoitaKoko(String kokonPolku) {

		try {
			this.koko.write(new FileOutputStream(kokonPolku), "TURTLE");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private Resource haeKorvaava(Resource res, Model model) {
		List<Resource> results = new ArrayList<>();
		StmtIterator iter = model.listStatements(res, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			results.add(iter.next().getResource());
		}
		if (results.size() > 1) {
			Collections.sort(results, new ResourceHierarchyComparator());
		} return results.get(0);
	}

	/*
	 * args[0] = vanhatUrit-KokoUrit-vastaavuudettiedosto
	 * args[1] = yson polku
	 * args[2] = kokon erikoisontologioiden polut ja tyypit
	 * args[3] = uusi vanhatUrit-KokoUrit-vastaavuudettiedosto
	 * args[4] = vanha KOKO
	 * args[5] = uusi KOKO
	 * args[6] = musta lista (kasitteet, joita ei haluta KOKOon)
	 * args[7] = kokon sisäisesti käyttämät repbacedby -tiedot
	 * 
	 * optional:
	 * args[8] = debug-koko ennen kuin urit muutetaan koko-ureiksi
	 */
	public static void main(String[] args) {
		Kokoaja2 kokoaja = new Kokoaja2(args[0]);

		if (args.length == 8)
			kokoaja.kokoa(args[1], args[2], args[4], args[3], args[6], args[7]);
		else if (args.length == 9)
			kokoaja.kokoaJaTuotaDebugKoko(args[1], args[2], args[4], args[3], args[6], args[7], args[8]);
		else
			System.out.println("Väärä määrä argumentteja");

		kokoaja.kirjoitaKoko(args[5]);
	}


	private void siivoaPoisTuplaVastaavuudet(HashSet<Statement> kaikkiLinkitetytKasitteet) {

		//poistetaan alkuun kaikki viitaukset itseensä
		HashSet<Statement> pois = new HashSet<>();
		for (Statement s : kaikkiLinkitetytKasitteet) {
			if (s.getSubject().equals(s.getObject()))
				pois.add(s);
		}
		kaikkiLinkitetytKasitteet.removeAll(pois);

		HashMap<Resource, HashSet<Statement>> kaikkiLinkitMap = new HashMap<Resource, HashSet<Statement>>();
		for (Statement s : kaikkiLinkitetytKasitteet) {

			Resource r1 = s.getSubject();
			Resource r2 = s.getResource();

			//Muodostetaan pussukat
			if (!kaikkiLinkitMap.containsKey(r1) && !kaikkiLinkitMap.containsKey(r2)) {
				HashSet<Statement> set = new HashSet<Statement>();
				set.add(s);
				set.add(s);
				kaikkiLinkitMap.put(r1, set);
				kaikkiLinkitMap.put(r2, set);

			}
			else if (kaikkiLinkitMap.containsKey(r1) && kaikkiLinkitMap.containsKey(r2)) {
				HashSet<Statement> s1 = kaikkiLinkitMap.get(r1);
				HashSet<Statement> s2 = kaikkiLinkitMap.get(r2);
				s1.addAll(s2);
				s1.add(s);
				s1.add(s);
				kaikkiLinkitMap.replace(r2, s1);
			}
			else if (kaikkiLinkitMap.containsKey(r1)) {
				HashSet<Statement> set = kaikkiLinkitMap.get(r1);
				set.add(s);
				kaikkiLinkitMap.put(r2, set);
			}
			else if (kaikkiLinkitMap.containsKey(r2)) {

				HashSet<Statement> set = kaikkiLinkitMap.get(r2);
				set.add(s);
				kaikkiLinkitMap.put(r1, set);
			}
		}
		HashSet<HashSet<Statement>> valueSet = new HashSet<>();
		valueSet.addAll(kaikkiLinkitMap.values());

		//Käy läpi kaikki pussukat (niitä on noin 55000)
		for (HashSet<Statement> pussukka : valueSet) {

			HashMap<String, HashSet<Statement>> tamanPussukanRomahtaneet = new HashMap<>();

			for (Statement s : pussukka) {
				String ns1 = s.getSubject().getNameSpace();
				String ns2 = s.getResource().getNameSpace();

				if (s.getSubject().getURI().equals(s.getResource().getURI())) continue; //ei huomioida exactMatcheja itsensa kanssa

				HashSet<Statement> set1 =  tamanPussukanRomahtaneet.containsKey(ns1) ? tamanPussukanRomahtaneet.get(ns1) : new HashSet<Statement>(); 
				set1.add(s);
				tamanPussukanRomahtaneet.put(ns1, set1);
				HashSet<Statement> set2 =  tamanPussukanRomahtaneet.containsKey(ns2) ? tamanPussukanRomahtaneet.get(ns2) : new HashSet<Statement>(); 
				set2.add(s);
				tamanPussukanRomahtaneet.put(ns2, set2);

			}


			for (HashSet<Statement> set : tamanPussukanRomahtaneet.values()) {
				if (set.size() > 1) {

					//jos pussukka romauttaa yhteen useamman käsitteen samasta nimiavaruudesta, valitaan vain yksi sopiva
					kaikkiLinkitetytKasitteet.removeAll(poistaNaistaTuplat(set));

				}
			}
		}
	}

	/*
	 * Filtteröi viittaukset ei-haluttuihin nimiavaruuksiin
	 * - YSA
	 * - Allars
	 * - Liito
	 * - STAMETA
	 * - TEROn linkittämät osaontologiat
	 */
	private boolean eiToivottuViittaus(Statement viittaus) {
		List<String> poisMatchNs = Arrays.asList(
				"http://www.yso.fi/onto/liito/" ,
				"http://www.yso.fi/onto/hpmulti/",
				"http://www.yso.fi/onto/ttl/",
				"http://www.yso.fi/onto/allars/" ,
				"http://www.yso.fi/onto/ysa/",
				"http://www.yso.fi/onto/stameta/");
		try {
		//poistetaan exactMatch-viittaukset
		if ( viittaus.getPredicate().equals(SKOS.exactMatch) && poisMatchNs.contains(viittaus.getResource().getNameSpace()) )
				return true;

		//poistetaan viittaukset itseensä
		if ( viittaus.getPredicate().equals(SKOS.exactMatch) && viittaus.getSubject().getURI().equals(viittaus.getResource().getURI()))
			return true;

		//poistetaan vanhentuneet tyypit
		if ( viittaus.getPredicate().equals(RDF.type) && viittaus.getResource().getURI().startsWith("http://www.yso.fi/onto/liito-meta"))
			return true;

		//ei oteta huomioon sellaisia YSO-käsitteitä, jotka eivät kuulu senhetkisen YSOn käsiteskeemaan
		if ( viittaus.getObject().isResource() &&
			 viittaus.getResource().getURI().startsWith("http://www.yso.fi/onto/yso/") &&
			 !this.ysoUrit.contains(viittaus.getResource().getURI()) )
			return true;
		} catch (Exception e) {
			System.out.println("Tripleä " +  viittaus + " ei otettu mukaan KOKOon");
			return true;
		}


		return false;
	}
	/*
	 * Filtteröi muut kun kuvailukäsitteet pois kokourivastaavuustalukosta
	 *
	 */
	private boolean kuuluuKokoUriTaulukkoon(Resource r) {

		if (r.getURI().startsWith("http://www.yso.fi/onto/"))
			return true;
		return false;

	}

	private HashSet<Statement> poistaNaistaTuplat(HashSet<Statement> set) {

		HashSet<Statement> result = new HashSet<>();

		////Säännöt tuplien poistamiseksi:

		for (Statement s : set) {
			boolean poisto = false;

			//Hylätään resurssit jolle ei löydy labelia
			if ( !poisto && !koko.contains(s.getResource(), SKOS.prefLabel) )
				poisto = true;

			//Hylätään deprekoitu resurssi
//			if (!poisto && koko.containsLiteral(s.getResource(), koko.getProperty("http://www.w3.org/2002/07/owl#deprecated"), true))
//				poisto = true;

			if (poisto)
				result.add(s);
		}

		// Tähän voi lisäksi lisätä hyvänä lisäna lisää lisäsääntöja, kuten vertailua prefLabeleiden kesken yms.

		if (result.size() +1 < set.size()) {
			//jos näyttää siltä että poistolistan koko ei riitä yksiselitteisen käsitteen löytämiseen
			ArrayList<Statement> list = new ArrayList<>(set);
			int pieninIndeksi = 0;

			//valitaan ryppään ylimmän tason käsite:
			for (int i = 1 ; i<list.size(); i++) {
				int levelCurrent = ResourceHierarchyComparator.hierarchyLevel(list.get(i).getResource());
				int levelReference = ResourceHierarchyComparator.hierarchyLevel(list.get(pieninIndeksi).getResource());
				if (levelCurrent > levelReference) {
					pieninIndeksi = i;
				} 
			}
			for (Statement s : set)
				if (!s.equals(list.get(pieninIndeksi)))
					result.add(s);
		}

		//System.out.println("Debuggailua: Poistetaan ryppäästä " + set.toString() + " seuraavat: " + result.toString());
		return result;
	}

}

class ResourceComparator implements Comparator<Resource> {

	@Override

	/**
	 * 	The result is a negative integer if the String representation of the first argument lexicographically precedes the argument string.
	 *  The result is a positive integer if the String representation of the first argument lexicographically follows the argument string.
	 *  The result is zero if the String representations of the arguments are equal;
	 *  compareTo returns 0 exactly when the arg0.equals(arg1) method would return true.
	 * 
	 *  @auth joelitak
	 *  @version 1.0
	 *  @since 2019-03-13
	 * 
	 */
	public int compare(Resource arg0, Resource arg1) {
		return arg0.toString().compareTo(arg1.toString());
	}
}

class ResourceHierarchyComparator implements Comparator<Resource> {

	static Property broader = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#broader");
	static Property narrower = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#narrower");

	@Override
	public int compare(Resource arg0, Resource arg1) {
		int levelOf0 = ResourceHierarchyComparator.hierarchyLevel(arg0);
		int levelOf1 = ResourceHierarchyComparator.hierarchyLevel(arg1);

		if (levelOf0 > levelOf1)
			return 1;
		if (levelOf0 < levelOf1)
			return -1;

		//if both are at the same level:
		int children0 = ResourceHierarchyComparator.numberOfSuccessors(arg0);
		int children1 = ResourceHierarchyComparator.numberOfSuccessors(arg1);

		if (children0 < children1)
			return 1;
		if (children0 > children1)
			return -1;

		//all else equal:
		return 0;
	}

	public static int hierarchyLevel(Resource res) {

		int dist = 0;
		Set<Statement> isot = res.listProperties(broader).toSet();
		while (!isot.isEmpty()) {

			Set<Statement> tmp = new HashSet<Statement>();
			for (Statement s :isot) {
				tmp.addAll(s.getResource().listProperties(broader).toSet());
			}
			isot = tmp;
			dist ++;
		}
		return dist;
	}

	public static int numberOfSuccessors(Resource res) {

		int child = 0;

		List<Statement> list = res.listProperties(narrower).toList();
		child += list.size();

		for (Statement s : list) {
			child += numberOfSuccessors(s.getResource());
		}

		return child;

	}

}