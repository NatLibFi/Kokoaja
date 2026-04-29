import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.SKOS;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.jena.rdf.model.Model;


public class Test {
	
	public static void haePaallekkaiset() throws IOException {
		Model yso = ModelFactory.createDefaultModel().read("/home/joelitak/git/Finto-data/vocabularies/yso/yso-vb-skos.ttl");
		HashMap<String, ArrayList<String>> map = Test.getDictionary("/home/joelitak/git/Kokoaja/kokoUriVastaavuudet-test.txt");
		map.forEach((key, values) -> {
			boolean hasYso = false;
			String prevYso = "";
			for (String uri : values) {
				
				if (uri.startsWith("http://www.yso.fi/onto/yso/")) {
					if (hasYso) {
						Resource prevYsoRes = yso.createResource(prevYso);
						Resource curYsoRes = yso.createResource(uri);
						String prevDep = "";
						String curDep = "";
						if (isDeprecated(prevYsoRes)) prevDep = " (deprecated)";
						if (isDeprecated(curYsoRes)) curDep = " (deprecated)";
						System.out.println(key);
						System.out.println("  " + prevYso + prevDep);
						System.out.println("  " + uri + curDep);
					}
					hasYso = true;
					prevYso = uri;
				}
			}
		});
		
	}
	private static boolean isDeprecated(Resource res) {
		if (res.hasProperty(OWL.deprecated))
			return true;
		return false;
	}
	
	private static HashMap<String, ArrayList<String>> getDictionary(String file) throws IOException {
		HashMap<String, ArrayList<String>> map = new HashMap<String, ArrayList<String>>();
		BufferedReader reader = new BufferedReader(new FileReader(file));
		while (reader.ready()) {
			String[] line = reader.readLine().split(" ");
			String koko = line[1];
			String onto = line[0];
			if (!map.containsKey(koko))
				map.put(koko, new ArrayList<String>());
			map.get(koko).add(onto);
		}
		return map;
	}

	public void doTheThing() {
		
		Model koko = ModelFactory.createDefaultModel().read("/home/joelitak/git/Finto-data/vocabularies/koko/koko-test.ttl");
		StmtIterator i = koko.listStatements(null, SKOS.prefLabel, null, "fi");
		HashMap<String, String> map = new HashMap<String, String>();
		while (i.hasNext()) {
			Statement s = i.next();
			String uri = s.getSubject().getURI();
			String label = s.getLiteral().getLexicalForm();
			
			if (map.containsKey(uri)) {
				System.out.println(uri + " : " + map.get(uri) + " , " + label);
				
			} else {
				map.put(uri, label);
				
			}
			
			
			
		}
		
	}
	
	public static void main(String[] args) {
		try {
			Test.haePaallekkaiset();
		} catch (Exception e) {}
	}

}
