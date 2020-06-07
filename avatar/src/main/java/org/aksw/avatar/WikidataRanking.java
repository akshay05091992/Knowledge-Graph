package org.aksw.avatar;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.aksw.triple2nl.*;
import org.apache.jena.graph.Node;

public class WikidataRanking {
	
	
	
	
	
	public List<Triple> rankingTripleset(List<Triple> tripleset) {
		int Threshold=20;
		List<Triple> triples= new ArrayList<Triple>();
		List<String> commonpredicate=new WikidataTest().readcommonpredicate();
		for(String s:commonpredicate) {
			for(Triple t:tripleset) {
				if (t.predicateMatches(NodeFactory.createLiteral(s))) {
					triples.add(t);
				}
			}
		}
		tripleset.removeAll(triples);
		
		
		if(triples.size()<Threshold) {
			int Numberoftriplesneeded=Threshold-tripleset.size();
			int i=0;
			for(Triple s:tripleset) {
				triples.add(s);
				i++;
				Numberoftriplesneeded=Numberoftriplesneeded-1;
				if(Numberoftriplesneeded==0) {
					break;
				}
			}
			
		}else if(triples.size()>Threshold) {
			int Numberoftriplesremoved=triples.size()-Threshold;
			for(int i=0;i<Numberoftriplesremoved;i++) {
				triples.remove(triples.size()-1);
			}
		}
		
		
		return triples;
	}
	
	

}
