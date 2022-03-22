import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.SKOS;

public class Muutostarkistin {

	
	/*
	 * KOKOn muutostarkistin
	 * - tarkistetaan onko koko-skos.ttl ja koko-skos_test.ttl välillä hukkunut käsitteitä
	 * 
	 */
	
	public Muutostarkistin(Model vanhaKoko, Model uusiKoko) throws IOException {
		ArrayList<Resource> kadonneetKäsitteet = new ArrayList<Resource>();
		Set<Resource> vanhat;
		Set<Resource> uudet;
		
		ResIterator vanhaIter = vanhaKoko.listSubjects();
		vanhat = vanhaIter.toSet();
		uudet = uusiKoko.listSubjects().toSet();
		
		List<String> lista = new LinkedList<String>();
		
		for (Resource r : vanhat) {
			if (r.getURI().startsWith("http://www.yso.fi/onto/koko/") && !uudet.contains(r))
				kadonneetKäsitteet.add(r);
		}
		Collections.sort(kadonneetKäsitteet, new ResourceComparator());
		
		System.out.println("Kadonneita oli " + kadonneetKäsitteet.size() + " kpl.\n\n");
		Collections.sort(kadonneetKäsitteet, new ResourceComparator());
		
		BufferedWriter writer = new BufferedWriter(new FileWriter("puuttuvat.txt"));
		
		for (Resource r : kadonneetKäsitteet) {
			Statement nameSt = r.getProperty(SKOS.prefLabel, "fi");
			String name = (nameSt != null) ? "\t\t "+nameSt.getLiteral().getLexicalForm() : "";
			Statement tarkenneSt = r.getProperty(SKOS.exactMatch);
			String tarkenne = (tarkenneSt != null) ? "\t\t (" + tarkenneSt.getResource() + ")" : "";
			System.out.println(r + name + tarkenne);
			writer.write(r + name + tarkenne + "\n");
		}
		writer.close();
	}

	public static void main(String[] args) {
		String location = args[0];
		System.out.println("Luetaan mallit sisään...");
		Model vanha = ModelFactory.createDefaultModel().read(location + "koko-skos.ttl");
		Model uusi = ModelFactory.createDefaultModel().read(location + "koko-skos_test.ttl");
		System.out.println("Aloitetaan vertailu...");
		
		try {
			new Muutostarkistin(vanha, uusi);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
class ResourceComparator implements Comparator<Resource> {

	@Override
	public int compare(Resource arg0, Resource arg1) {
		String luku0 = arg0.getLocalName();
		luku0 = luku0.substring(luku0.indexOf("p")+1);
		int l0 = Integer.parseInt(luku0);

		String luku1 = arg1.getLocalName();
		luku1 = luku1.substring(luku1.indexOf("p")+1);
		int l1 = Integer.parseInt(luku1);
		
		if (l0 > l1)
			return 1;
		if (l0 < l1)
			return -1;
		return 0;
	}
	
	
}
