package org.aksw.avatar;

import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import java.util.ArrayList;
import java.util.List;

import org.aksw.avatar.gender.Gender;
import org.apache.jena.graph.Triple;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.QueryResults;
import org.openrdf.query.TupleQuery;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sparql.SPARQLRepository;
import org.aksw.triple2nl.*;

public class WikidataVerbalizer {
	private NLGFactory nlgFactory;
	private Realiser realiser;
	
	public String Verbalize(List<Triple> input,String NationalityIdentifier) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		String text="";
		Gender g;
		String subject="";
		String nationality="";
		List<NLGElement> result = new ArrayList<NLGElement>();
		if(NationalityIdentifier != "") {
			nationality=getNationality(NationalityIdentifier);
		}
		if(input.get(0).getPredicate().toString().equalsIgnoreCase("\"instance_of\"")) {
			if(input.get(0).getObject().toString().equalsIgnoreCase("\"human\"")) {
				subject=input.get(0).getSubject().toString();
				if(input.get(1).getObject().toString().equalsIgnoreCase("\"male\"")) {
					g = Gender.MALE;
				}else if(input.get(1).getObject().toString().equalsIgnoreCase("\"female\"")) {
					g= Gender.FEMALE;
				}
				else {
					g=Gender.UNKNOWN;
				}
				String occupation = new TripleConverter().getoccupation(input);
				
				if(isAlive(input)) {
					text=new WikidataTest().orchestrator(subject).replace("_", " ")+" is a "+nationality+" "+occupation;
					
				}else {
					text=new WikidataTest().orchestrator(subject).replace("_", " ")+" was a "+nationality+" "+occupation;
				}
				
			}
		}
		
		
		return text;
	}
	
	
	
	public String getNationality(String country) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		String text="";
		country=country.replaceAll("\"", "").replaceAll("_", " ").replaceAll("http://www.wikidata.org/entity/", "");
		SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
		sparqlRepository.initialize();
		RepositoryConnection sparqlConnection = sparqlRepository.getConnection();
		String query = "SELECT ?demonym WHERE {" +
				"  wd:"+country+" wdt:P1549 ?demonym ." + 
				"  FILTER (LANG(?demonym) = \"en\") . }";
		TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
		for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
			text=new WikidataTest().orchestrator(bs.getValue("demonym").toString());
		}
		
		
		return text;
	}
	
	public boolean isAlive(List<Triple> input) {
		boolean b=true;
		for(Triple t:input) {
			if(t.getPredicate().toString().equalsIgnoreCase("\"date_of_death\"")) {
				b=false;
			}
		}
		return b;
	}
	

}
