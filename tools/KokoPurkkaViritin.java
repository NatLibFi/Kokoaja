import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;

public class KokoPurkkaViritin {

	/*
	 * Kokon tarkistusvaiheessa huomataan silloin tällöin että KOKOsta puuttuu käsitteitä, jotka erillinen poistuneiden tarkistin huomaa
	 * Tämä ohjelma kopioi urilistan perusteella kokoon vanhojen käsitteiden tietoja edellisestä kokosta.
	 * 
	 */
	
	public KokoPurkkaViritin(String kokoPath) throws IOException {
		String puuttuvatUrit = "puuttuvat.txt";
		Model koko = ModelFactory.createDefaultModel().read(kokoPath + "koko-skos.ttl");
		Model uusKoko = ModelFactory.createDefaultModel().read(kokoPath + "koko-test.ttl");
		
		BufferedReader reader = new BufferedReader(new FileReader(puuttuvatUrit));
		int k=1;
		while (reader.ready()) {
			String line = reader.readLine();
			String uri = line.split("\t")[0];
			System.out.println(k +". Lisätään " + uri);
			Resource r = koko.getResource(uri);
			StmtIterator i = r.listProperties();
			uusKoko.add(i);
			k++;
		}
		uusKoko.write((new FileWriter(kokoPath + "koko-test_korjattu.ttl")), "TTL");
	}

	public static void main(String[] args) throws Exception {
		
		new KokoPurkkaViritin(args[0]);
	}

}
