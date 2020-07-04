package org.aksw.avatar;

import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aksw.triple2nl.gender.Gender;
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

	public String Verbalize(List<Triple> input, String NationalityIdentifier, String subjectIdentifier)
			throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		String text = "";
		Gender g = Gender.UNKNOWN;
		String subject = "";
		String nationality = "";
		String occupation = "";
		boolean isalive = true;
		int indexGender = 0;
		int indexInstance = 0;
		List<NLGElement> result = new ArrayList<NLGElement>();
		if (NationalityIdentifier != "") {
			nationality = getNationality(NationalityIdentifier);
		}

		if (input.get(0).getPredicate().toString().equalsIgnoreCase("\"instance_of\"")) {
			if (input.get(0).getObject().toString().equalsIgnoreCase("\"human\"")) {
				subject = input.get(0).getSubject().toString();
				for (int i = 0; i < input.size(); i++) {
					if (input.get(i).getPredicate().toString().equalsIgnoreCase("\"sex_or_gender\"")
							|| input.get(i).getPredicate().toString().contains("sex_or_gender")) {
						indexGender = i;
					}
					if (input.get(i).getObject().toString().equalsIgnoreCase("\"human\"")) {
						indexInstance = i;
					}
				}

				if (indexInstance != 0 && indexGender != 0) {
					input.remove(indexInstance);
					input.remove(indexGender - 1);
				} else if(indexInstance != 0 && indexGender == 0) {
					input.remove(indexGender);
				}
				indexGender = 0;
				input.remove(indexGender);
				int count = 0;
				String gender = WikidataTest.getGender(subjectIdentifier);
				if (gender.trim().equalsIgnoreCase("male")) {
					g = Gender.MALE;
				} else if (gender.trim().equalsIgnoreCase("female")) {
					g = Gender.FEMALE;
				}
				isalive = isAlive(subjectIdentifier);
				Map<String, List<Triple>> resultdata = new TripleConverter().getoccupation(input);
				for (Map.Entry<String, List<Triple>> entry : resultdata.entrySet()) {
					if (count < 5) {
						occupation = entry.getKey();
						input = entry.getValue();
					}
					count++;
				}

				if (occupation.trim().equalsIgnoreCase("")) {
					occupation = WikidataTest.getOccupationFromDb(subjectIdentifier);
				}

				if (isalive) {
					if (occupation.trim().equalsIgnoreCase("")) {
						text = WikidataTest.orchestrator(subject).replace("_", " ") + " is a " + nationality + " "
								+ "Person.";
					} else {
						text = WikidataTest.orchestrator(subject).replace("_", " ") + " is a " + nationality + " "
								+ occupation + ".";
					}

				} else {
					text = WikidataTest.orchestrator(subject).replace("_", " ") + " was a " + nationality + " "
							+ occupation+".";
					isalive = false;
				}

			}
		}
		text += new TripleConverter().textgeneration(input, g, isalive);

		System.out.println(input.toString());
		return text;
	}

	public String getNationality(String country)
			throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		String text = "";
		country = country.replaceAll("\"", "").replaceAll("_", " ").replaceAll("http://www.wikidata.org/entity/", "");
		SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
		sparqlRepository.initialize();
		RepositoryConnection sparqlConnection = sparqlRepository.getConnection();
		String query = "SELECT ?demonym WHERE {" + "  wd:" + country + " wdt:P1549 ?demonym ."
				+ "  FILTER (LANG(?demonym) = \"en\") . }";
		TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
		for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
			text = new WikidataTest().orchestrator(bs.getValue("demonym").toString());
		}

		return text;
	}

	public boolean isAlive(String subject) {
		boolean b = true;
		try {
			if (!WikidataTest.getdeathDate(subject).equalsIgnoreCase("")) {
				b = false;
			}
		} catch (RepositoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedQueryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return b;
	}

}
