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
import org.openrdf.repository.sparql.SPARQLRepository;

import org.aksw.triple2nl.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.jena.graph.Triple;
import org.joda.time.DateTime;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.NodeFactory;

public class WikidataTest {
	
	private static TripleConverter converter;
	
	public static void main(String[]args) throws QueryEvaluationException, RepositoryException, MalformedQueryException, TupleQueryResultHandlerException {
		SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
		List<Triple> triples = new ArrayList<Triple>();
		sparqlRepository.initialize();
		ValueFactory vf = new ValueFactoryImpl();
		//String subjectIdentifier="Q615";Q9488
		String subjectIdentifier="Q9488";
		String predicateIdentifier="";
		String objectIdentifier="";
		String subject=orchestrator(new WikidataTest().findlabels(vf.createLiteral(subjectIdentifier)));
		String predicate="";
		String object="";
		RepositoryConnection sparqlConnection = sparqlRepository.getConnection();
		
		String query = "SELECT ?Predicate ?Object  WHERE {"
		        + "wd:"+subjectIdentifier+"?Predicate ?Object ."
		        + "}";

		TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
		for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
			if(bs.getValue("Predicate").toString().contains("http://www.wikidata.org/prop/direct/") && (bs.getValue("Object").toString().contains("http://www.wikidata.org/entity/")||bs.getValue("Object").toString().contains("<http://www.w3.org/2001/XMLSchema#dateTime>"))) {
				
				predicate=orchestrator(new WikidataTest().findlabels(bs.getValue("Predicate")));
				if(bs.getValue("Object").toString().contains("<http://www.w3.org/2001/XMLSchema#dateTime>")) {
					object=bs.getValue("Object").toString().split("T")[0].replace("\"", "");
					triples.add(Triple.create(NodeFactory.createLiteral(subject), NodeFactory.createLiteral(predicate),NodeFactory.createLiteral(object,XSDDatatype.XSDdate)));
				}else {
				object=orchestrator(new WikidataTest().findlabels(bs.getValue("Object")));
				triples.add(Triple.create(NodeFactory.createLiteral(subject), NodeFactory.createLiteral(predicate),NodeFactory.createLiteral(object)));
				}
				
				
			}
			
		}
		
		System.out.println(triples.size());
	}
	
	public static String orchestrator(String literal) {
		literal=literal.replaceAll("@en", "");
		literal=literal.replaceAll("\"", "");
		literal=literal.replaceAll(" ", "_");
		return literal;
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

}
