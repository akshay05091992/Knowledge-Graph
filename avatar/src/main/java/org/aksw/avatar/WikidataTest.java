package org.aksw.avatar;

import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.QueryResults;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sparql.SPARQLConnection;
import org.openrdf.repository.sparql.SPARQLRepository;

import org.aksw.triple2nl.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.jena.graph.Triple;
import org.joda.time.DateTime;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.NodeFactory;

public class WikidataTest {
	
	private static TripleConverter converter;
	
	public static String wikidatamain(String subjectinput,String predicateinput) throws QueryEvaluationException, RepositoryException, MalformedQueryException, TupleQueryResultHandlerException {
		SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
		List<Triple> triples = new ArrayList<Triple>();
		sparqlRepository.initialize();
		ValueFactory vf = new ValueFactoryImpl();
		//String subjectIdentifier="Q615";Q9488;Q25369;Q22686;Q937
		String subjectIdentifier=subjectinput;
		String predicateIdentifier=predicateinput;
		String objectIdentifier="";
		String subject=orchestrator(new WikidataTest().findlabels(vf.createLiteral(subjectIdentifier)));
		String predicate="";
		if(!predicateIdentifier.isEmpty()) {
			predicate=orchestrator(new WikidataTest().findlabels(vf.createLiteral(predicateIdentifier)));
		}
		String object="";
		String NationalityIdentifier="";
		RepositoryConnection sparqlConnection = sparqlRepository.getConnection();
		String query="";
		Integer count = 0;
		Integer count1 = 0;
		
		
		if(!predicateIdentifier.isEmpty()) {
			query = "SELECT  ?Object  WHERE {"
		        + "wd:"+subjectIdentifier+" wdt:"+predicateIdentifier+" ?Object ."
		        + "}";
		}else {
			query = "SELECT ?Predicate WHERE"
					+ " {wd:"+subjectIdentifier+" ?Predicate ?Object .  }"
					+ " GROUP BY ?Predicate"
					+ " ORDER BY DESC(COUNT(?Object)) LIMIT 60";
		}	
		TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
		if(predicateIdentifier.isEmpty()) {
			for (BindingSet bs1 : QueryResults.asList(tupleQuery.evaluate())) {
				if(bs1.getValue("Predicate").toString().contains("http://www.wikidata.org/prop/direct/")){
					if(count1 > 20){
						break;
					}
					count = 0;
					predicate = bs1.getValue("Predicate").toString().replaceAll("http://www.wikidata.org/prop/direct/", "");
					query = "SELECT  ?Object  WHERE {"
					        + "wd:"+subjectIdentifier+" wdt:"+predicate+" ?Object ."
					        + "}";
					//System.out.println("query object "+query);
					count1++;
					tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
					for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
						if(bs1.getValue("Predicate").toString().contains("http://www.wikidata.org/prop/direct/") && (bs.getValue("Object").toString().contains("http://www.wikidata.org/entity/")||bs.getValue("Object").toString().contains("<http://www.w3.org/2001/XMLSchema#dateTime>"))) {
							
							predicate=orchestrator(new WikidataTest().findlabels(bs1.getValue("Predicate")));
							if(predicate.equals("country_of_citizenship")) {
								NationalityIdentifier=bs.getValue("Object").toString();
							}
							if(bs.getValue("Object").toString().contains("<http://www.w3.org/2001/XMLSchema#dateTime>")) {
								object=bs.getValue("Object").toString().split("T")[0].replace("\"", "");
								triples.add(Triple.create(NodeFactory.createLiteral(subject), NodeFactory.createLiteral(predicate),NodeFactory.createLiteral(object,XSDDatatype.XSDdate)));
							}else {
							object=orchestrator(new WikidataTest().findlabels(bs.getValue("Object")));
							if(count < 3){
								triples.add(Triple.create(NodeFactory.createLiteral(subject), NodeFactory.createLiteral(predicate),NodeFactory.createLiteral(object)));
								count++;
							}else{
								break;
							}
							
							}
							
							
						}
						
					}
					
				}
			}
		}else {
			for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
				object=orchestrator(new WikidataTest().findlabels(bs.getValue("Object")));
				triples.add(Triple.create(NodeFactory.createLiteral(subject), NodeFactory.createLiteral(predicate),NodeFactory.createLiteral(object)));
			}
		}
		triples.add(Triple.create(NodeFactory.createLiteral(subject), NodeFactory.createLiteral("date_of_birth"), NodeFactory.createLiteral(getbirthPlace(subjectIdentifier).split("T")[0].replace("\"", ""),XSDDatatype.XSDdate)));
		object = getdeathDate(subjectIdentifier);
		if(!object.equalsIgnoreCase("") || object != null){
			triples.add(Triple.create(NodeFactory.createLiteral(subject), NodeFactory.createLiteral("date_of_death"), NodeFactory.createLiteral(getdeathDate(subjectIdentifier).split("T")[0].replace("\"", ""),XSDDatatype.XSDdate)));
		}
		triples.add(Triple.create(NodeFactory.createLiteral(subject), NodeFactory.createLiteral("instance_of"), NodeFactory.createLiteral(getInstanceOf(subjectIdentifier))));
		object = getCitizenship(subjectIdentifier);
		if((!object.equalsIgnoreCase("") || object != null) && (NationalityIdentifier.equalsIgnoreCase(""))){
			NationalityIdentifier = object;
		}
		List<Triple> orderedTriples=new WikidataRanking().rankingTripleset(triples);
		return new WikidataVerbalizer().Verbalize(orderedTriples,NationalityIdentifier,subjectIdentifier);
	}
	
	public static String orchestrator(String literal) {
		literal=literal.replaceAll("@en", "");
		literal=literal.replaceAll("\"", "");
		literal=literal.replaceAll(" ", "_");
		return literal;
	}
	public List<String> readcommonpredicate() {
		List<String> predicate=new ArrayList<String>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/common-predicate.txt")))) {
		    String line;
		    while ((line = br.readLine()) != null) {
		       // process the line.
		    	predicate.add(line);
		    }
		}catch(IOException e) {
			e.printStackTrace();
		}
		
		
		return predicate;
		
	}
	
	public String findlabels(Value value) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		String labels="";
		SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
		sparqlRepository.initialize();
		RepositoryConnection sparqlConnection = sparqlRepository.getConnection();
		String val=value.toString().replaceAll("\"", "").replace("http://www.wikidata.org/prop/direct/", "").replace("http://www.wikidata.org/entity/", "").replaceAll("\"", "");
		String query = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> PREFIX wd: <http://www.wikidata.org/entity/> select  * where {"
		        + "wd:"+val+" rdfs:label ?label ."
		        + " FILTER (langMatches( lang(?label), \"EN\" ) ) } LIMIT 1";
		
		TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
		for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
			labels= bs.getValue("label").toString();
		}
		return labels;
		
	}
	
	public static String getImage(String subject) throws RepositoryException, MalformedQueryException, QueryEvaluationException{
		String query = "";
		String img = "";
		if(subject == null || subject == ""){
			return "";
		}else{
			query = "SELECT ?Object  WHERE {wd:"+subject+" wdt:P18 ?Object .}";
			System.out.println(query);
			SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
			sparqlRepository.initialize();
			RepositoryConnection sparqlConnection = sparqlRepository.getConnection();
			TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
			for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
				img = bs.getValue("Object").toString();
			}
		}
		return img;
	}
	public static String getbirthPlace(String subject) throws RepositoryException, MalformedQueryException, QueryEvaluationException{
		String query = "";
		String dob = "";
		if(subject == null || subject == ""){
			return "";
		}else{
			query = "SELECT ?Object  WHERE {wd:"+subject+" wdt:P569 ?Object .}";
			//System.out.println(query);
			SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
			sparqlRepository.initialize();
			RepositoryConnection sparqlConnection = sparqlRepository.getConnection();
			TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
			for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
				dob = bs.getValue("Object").toString();
			}
		}
		return dob;
	}
	public static String getdeathDate(String subject) throws RepositoryException, MalformedQueryException, QueryEvaluationException{
		String query = "";
		String dob = "";
		if(subject == null || subject == ""){
			return "";
		}else{
			query = "SELECT ?Object  WHERE {wd:"+subject+" wdt:P570 ?Object .}";
			System.out.println(query);
			SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
			sparqlRepository.initialize();
			RepositoryConnection sparqlConnection = sparqlRepository.getConnection();
			TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
			for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
				dob = bs.getValue("Object").toString();
			}
		}
		return dob;
	}
	
	public static String getInstanceOf(String subject) throws RepositoryException, MalformedQueryException, QueryEvaluationException{
		String query = "";
		String dob = "";
		if(subject == null || subject == ""){
			return "";
		}else{
			query = "SELECT ?Object  WHERE {wd:"+subject+" wdt:P31 ?Object .}";
			System.out.println(query);
			SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
			sparqlRepository.initialize();
			RepositoryConnection sparqlConnection = sparqlRepository.getConnection();
			TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
			for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
				dob = orchestrator(new WikidataTest().findlabels(bs.getValue("Object")));
			}
		}
		return dob;
	}
	
	public static String getGender(String subject) throws RepositoryException, MalformedQueryException, QueryEvaluationException{
		String query = "";
		String dob = null;
		if(subject == null || subject == ""){
			return dob;
		}else{
			query = "SELECT ?Object  WHERE {wd:"+subject+" wdt:P21 ?Object .}";
			System.out.println(query);
			SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
			sparqlRepository.initialize();
			RepositoryConnection sparqlConnection = sparqlRepository.getConnection();
			TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
			for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
				dob = orchestrator(new WikidataTest().findlabels(bs.getValue("Object"))); 
			}
		}
		return dob;
	}
	
	public static String getCitizenship(String subject) throws RepositoryException, MalformedQueryException, QueryEvaluationException{
		String query = "";
		String dob = null;
		if(subject == null || subject == ""){
			return dob;
		}else{
			query = "SELECT ?Object  WHERE {wd:"+subject+" wdt:P27 ?Object .}";
			System.out.println(query);
			SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
			sparqlRepository.initialize();
			RepositoryConnection sparqlConnection = sparqlRepository.getConnection();
			TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
			for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
				dob = bs.getValue("Object").toString(); 
			}
		}
		return dob;
	}



}
