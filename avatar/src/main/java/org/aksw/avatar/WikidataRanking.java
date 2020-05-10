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
		
		HashMap<Node,Integer> counts = new HashMap<Node,Integer>();

        for(int i = 0; i < tripleset.size(); i++) {
            if(counts.containsKey(tripleset.get(i).getPredicate())) {
                Integer c = counts.get(tripleset.get(i).getPredicate()) + 1;
                counts.put(tripleset.get(i).getPredicate(), c);
            }
            else {
                counts.put(tripleset.get(i).getPredicate(),1);
            }
        }
        
        ValueComparator<Node,Integer> bvc = new ValueComparator<Node,Integer>(counts);
        TreeMap<Node,Integer> sortedMap = new TreeMap<Node,Integer>(bvc);
        sortedMap.putAll(counts);

        ArrayList<Node> output = new ArrayList<Node>();
        for(Node i : sortedMap.keySet()) {
            for(int c = 0; c < sortedMap.get(i); c++) {
                output.add(i);
            }
        }
        
		if(triples.size()<Threshold) {
			int Numberoftriplesneeded=Threshold-tripleset.size();
			int i=0;
			for(Triple s:tripleset) {
				if(s.predicateMatches(output.get(i))) {
					triples.add(s);
					i++;
					Numberoftriplesneeded=Numberoftriplesneeded-1;
				}
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
