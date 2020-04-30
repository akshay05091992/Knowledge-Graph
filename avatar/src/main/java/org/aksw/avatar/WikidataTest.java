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
import java.util.List;

import org.apache.jena.graph.Triple;
import org.apache.jena.graph.NodeFactory;

public class WikidataTest {
	
	private static TripleConverter converter;
	
	public static void main(String[]args) throws QueryEvaluationException, RepositoryException, MalformedQueryException, TupleQueryResultHandlerException {
		SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
		List<Triple> triples = new ArrayList<Triple>();
		sparqlRepository.initialize();
		ValueFactory vf = new ValueFactoryImpl();
		String predicateIdentifier="P22";
		String objectIdentifier = "Q1339";
		String subject="";
		String predicate=orchestrator(new WikidataTest().findlabels(vf.createLiteral(predicateIdentifier)));
		String object=orchestrator(new WikidataTest().findlabels(vf.createLiteral(objectIdentifier)));
		RepositoryConnection sparqlConnection = sparqlRepository.getConnection();

		String query = "SELECT ?child ?childLabel WHERE {"
		        + "?child wdt:"+predicateIdentifier+" wd:"+objectIdentifier+"."
		        + " SERVICE wikibase:label { bd:serviceParam wikibase:language \"[AUTO_LANGUAGE]\".}}";

		TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
		for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
		    //System.out.println(new WikidataTest().findlabels(bs.getValue("childLabel")));
			subject=orchestrator(new WikidataTest().findlabels(bs.getValue("childLabel")));
			triples.add(Triple.create(NodeFactory.createURI(subject), NodeFactory.createURI(predicate), NodeFactory.createURI(object)));
		}
		
		System.out.println(triples.toString());
		//String text = converter.convert(triples);
		//System.out.println(text);
		//System.out.println(orchestrator(new WikidataTest().findlabels(vf.createLiteral("Q1339"))));
		
		//tupleQuery.evaluate(new SPARQLResultsXMLWriter(System.out));
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
		String val=value.toString().replaceAll("\"", "");
		//System.out.println(val);
		String query = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> PREFIX wd: <http://www.wikidata.org/entity/> select  * where {"
		        + "wd:"+val+" rdfs:label ?label ."
		        + " FILTER (langMatches( lang(?label), \"EN\" ) ) } LIMIT 1";
		
		//System.out.println(query);

		TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
		for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
			labels= bs.getValue("label").toString();
		}
		return labels;
		
	}

}
