/*
 * #%L
 * Triple2NL
 * %%
 * Copyright (C) 2015 Agile Knowledge Engineering and Semantic Web (AKSW)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
/**
 * 
 */
package org.aksw.triple2nl;

import com.google.common.collect.Lists;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.LiteralLabel;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import net.sf.extjwnl.dictionary.Dictionary;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.jena_sparql_api.model.QueryExecutionFactoryModel;
import org.aksw.triple2nl.converter.DefaultIRIConverter;
import org.aksw.triple2nl.converter.IRIConverter;
import org.aksw.triple2nl.converter.LiteralConverter;
import org.aksw.triple2nl.gender.Gender;
import org.aksw.triple2nl.gender.GenderDetector;
import org.aksw.triple2nl.gender.DictionaryBasedGenderDetector;
import org.aksw.triple2nl.nlp.relation.BoaPatternSelector;
import org.aksw.triple2nl.nlp.stemming.PlingStemmer;
import org.aksw.triple2nl.property.PropertyVerbalization;
import org.aksw.triple2nl.property.PropertyVerbalizationType;
import org.aksw.triple2nl.property.PropertyVerbalizer;
import org.aksw.triple2nl.util.GenericType;
import org.apache.commons.collections15.ListUtils;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simplenlg.features.Feature;
import simplenlg.features.InternalFeature;
import simplenlg.features.LexicalFeature;
import simplenlg.features.NumberAgreement;
import simplenlg.features.Tense;
import simplenlg.framework.*;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;
import uk.ac.manchester.cs.owl.owlapi.OWLDataPropertyImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLObjectPropertyImpl;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Convert triple(s) into natural language.
 * 
 * @author Lorenz Buehmann
 * 
 */
public class TripleConverter {

	private static final Logger logger = LoggerFactory.getLogger(TripleConverter.class);

	private static String DEFAULT_CACHE_BASE_DIR = System.getProperty("java.io.tmpdir");
	private static String DEFAULT_CACHE_DIR = DEFAULT_CACHE_BASE_DIR + "/triple2nl-cache";

	private NLGFactory nlgFactory;
	private Realiser realiser;

	private IRIConverter uriConverter;
	private LiteralConverter literalConverter;
	private PropertyVerbalizer pp;
	private SPARQLReasoner reasoner;

	private boolean determinePluralForm = false;
	// show language as adjective for literals
	private boolean considerLiteralLanguage = true;
	// encapsulate string literals in quotes ""
	private boolean encapsulateStringLiterals = true;
	// for multiple types use 'as well as' to coordinate the last type
	private boolean useAsWellAsCoordination = true;

	private boolean returnAsSentence = true;

	private boolean useGenderInformation = true;

	private GenderDetector genderDetector;

	public TripleConverter() {
		this(new QueryExecutionFactoryModel(ModelFactory.createDefaultModel()), DEFAULT_CACHE_DIR,
				Lexicon.getDefaultLexicon());
	}

	public TripleConverter(SparqlEndpoint endpoint) {
		this(endpoint, DEFAULT_CACHE_DIR);
	}

	public TripleConverter(QueryExecutionFactory qef, String cacheDirectory, Dictionary wordnetDirectory) {
		this(qef, null, null, cacheDirectory, wordnetDirectory, null);
	}

	public TripleConverter(SparqlEndpoint endpoint, String cacheDirectory) {
		this(endpoint, cacheDirectory, null);
	}

	public TripleConverter(SparqlEndpoint endpoint, String cacheDirectory, Dictionary wordnetDirectory) {
		this(new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs()), null, null,
				cacheDirectory, wordnetDirectory, Lexicon.getDefaultLexicon());
	}

	public TripleConverter(SparqlEndpoint endpoint, String cacheDirectory, Dictionary wordnetDirectory,
			Lexicon lexicon) {
		this(new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs()), null, null,
				cacheDirectory, wordnetDirectory, lexicon);
	}

	public TripleConverter(QueryExecutionFactory qef, IRIConverter uriConverter, String cacheDirectory,
			Dictionary wordnetDirectory) {
		this(qef, null, uriConverter, cacheDirectory, wordnetDirectory, Lexicon.getDefaultLexicon());
	}

	public TripleConverter(QueryExecutionFactory qef, String cacheDirectory, Lexicon lexicon) {
		this(qef, null, null, cacheDirectory, null, lexicon);
	}

	public TripleConverter(QueryExecutionFactory qef, PropertyVerbalizer propertyVerbalizer, IRIConverter uriConverter,
			String cacheDirectory, Dictionary wordnetDirectory, Lexicon lexicon) {
		if (uriConverter == null) {
			uriConverter = new DefaultIRIConverter(qef, cacheDirectory);
		}
		this.uriConverter = uriConverter;

		if (propertyVerbalizer == null) {
			propertyVerbalizer = new PropertyVerbalizer(uriConverter, wordnetDirectory);
		}
		pp = propertyVerbalizer;

		if (lexicon == null) {
			lexicon = Lexicon.getDefaultLexicon();
		}

		nlgFactory = new NLGFactory(lexicon);
		realiser = new Realiser(lexicon);

		literalConverter = new LiteralConverter(uriConverter);
		literalConverter.setEncapsulateStringLiterals(encapsulateStringLiterals);

		reasoner = new SPARQLReasoner(qef);

		genderDetector = new DictionaryBasedGenderDetector();
	}

	/**
	 * Return a textual representation for the given triple.
	 *
	 * @param t
	 *            the triple to convert
	 * @return the textual representation
	 */
	public String convert(Triple t) {
		return convert(t, false);
	}

	public Map<String, List<Triple>> getoccupation(List<Triple> input) {
		CoordinatedPhraseElement combinedObject = nlgFactory.createCoordinatedPhrase();
		List<Triple> toberemoved = new ArrayList<Triple>();
		for (Triple t : input) {
			if (t.getPredicate().toString().equals("\"occupation\"")) {
				toberemoved.add(t);
				combinedObject.addCoordinate(orchestrator(t.getObject().toString()));
			}
		}
		input.removeAll(toberemoved);
		Map<String, List<Triple>> result = new HashMap<String, List<Triple>>();
		result.put(realiser.realiseSentence(combinedObject), input);
		return result;
	}

	public static String orchestrator(String literal) {
		literal = literal.replaceAll("@en", "");
		literal = literal.replaceAll("\"", "");
		literal = literal.replaceAll("_", " ");
		return literal;
	}

	@SuppressWarnings("deprecation")
	public String textgeneration(List<Triple> input, Gender g, boolean isalive) {
		String text = "";
		List<SPhraseSpec> sentenseclause = new ArrayList<SPhraseSpec>();
		Set<String> uniquepredicate = new HashSet<String>();
		for (Triple t : input) {
			SPhraseSpec sentence = nlgFactory.createClause();
			NLGElement subject;
			if (g.equals(Gender.MALE)) {
				if (processpredicate(t.getPredicate().toString()).equalsIgnoreCase("educated at")
						|| processpredicate(t.getPredicate().toString()).equalsIgnoreCase("influenced by")
						|| processpredicate(t.getPredicate().toString()).equalsIgnoreCase("member of")) {
					subject = nlgFactory.createStringElement("He was");
				} else {
					subject = nlgFactory.createStringElement("His");
				}
			} else if (g.equals(Gender.FEMALE)) {
				if (processpredicate(t.getPredicate().toString()).equalsIgnoreCase("educated at")
						|| processpredicate(t.getPredicate().toString()).equalsIgnoreCase("influenced by")
						|| processpredicate(t.getPredicate().toString()).equalsIgnoreCase("member of")) {
					subject = nlgFactory.createStringElement("She was");
				} else {
					subject = nlgFactory.createStringElement("Her");
				}
			} else {
				subject = nlgFactory.createStringElement("It");
			}
			NPPhraseSpec subjectnoun = nlgFactory.createNounPhrase(subject);
			boolean flag = false;
			if (processpredicate(t.getPredicate().toString()).equalsIgnoreCase("is")) {
				subjectnoun.setFeature(Feature.POSSESSIVE, Boolean.FALSE);
				flag = true;
			} else {
				subjectnoun.setFeature(Feature.POSSESSIVE, Boolean.FALSE);
			}
			sentence.setSubject(subjectnoun);
			if (!uniquepredicate.contains(processpredicate(t.getPredicate().toString()))) {
				NLGElement verb = nlgFactory.createStringElement(processpredicate(t.getPredicate().toString()));
				NPPhraseSpec verbnoun = nlgFactory.createNounPhrase(verb);
				verbnoun.setFeature(Feature.NUMBER, NumberAgreement.SINGULAR);
				sentence.setVerb(verbnoun);
				CoordinatedPhraseElement conjugatingobjects = nlgFactory.createCoordinatedPhrase();
				if (isDate(t.getObject().toString())) {
					String datestring = processDateLiteral(t.getObject().toString());
					DateFormat format1 = new SimpleDateFormat("yyyy-MM-dd");
					try {
						Date date = format1.parse(datestring);

						DateFormat format2 = new SimpleDateFormat("MMMMM dd, yyyy");
						String dateString = format2.format(date);
						NPPhraseSpec determiner;
						if (isalive) {
							determiner = nlgFactory.createNounPhrase("is");
						} else {
							determiner = nlgFactory.createNounPhrase("was");
						}
						NLGElement datedata = nlgFactory.createStringElement(dateString);
						NPPhraseSpec firstclause = nlgFactory.createNounPhrase(datedata);
						firstclause.setDeterminer(determiner);
						conjugatingobjects.addCoordinate(firstclause);
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				} else {
					NPPhraseSpec determiner;
					if (!flag) {
						if (isalive) {
							determiner = nlgFactory.createNounPhrase("is");
						} else {
							determiner = nlgFactory.createNounPhrase("was");
						}
					} else {
						determiner = nlgFactory.createNounPhrase("a");
					}
					NLGElement data = nlgFactory.createStringElement(processobject(t.getObject().toString()));
					NPPhraseSpec firstclause = nlgFactory.createNounPhrase(data);
					if (!"educated at".equalsIgnoreCase(processpredicate(t.getPredicate().toString()))
							&& !"influenced by".equalsIgnoreCase(processpredicate(t.getPredicate().toString()))
							&& !"member of".equalsIgnoreCase(processpredicate(t.getPredicate().toString()))) {
						firstclause.setDeterminer(determiner);
					}
					conjugatingobjects.addCoordinate(firstclause);

				}
				sentence.setFeature(Feature.NUMBER, NumberAgreement.SINGULAR);
				conjugatingobjects.setFeature(Feature.RAISE_SPECIFIER, Boolean.FALSE);
				if (!"educated at".equalsIgnoreCase(processpredicate(t.getPredicate().toString()))
						&& !"influenced by".equalsIgnoreCase(processpredicate(t.getPredicate().toString()))
						&& !"member of".equalsIgnoreCase(processpredicate(t.getPredicate().toString()))) {
					if (!flag) {
						if (isalive) {
							conjugatingobjects.setFeature(InternalFeature.SPECIFIER, "is");
						} else {
							conjugatingobjects.setFeature(InternalFeature.SPECIFIER, "was");
						}
					} else {
						conjugatingobjects.setFeature(InternalFeature.SPECIFIER, "a");
					}
				}
				sentence.setFeature(Feature.RAISE_SPECIFIER, Boolean.FALSE);
				sentence.setFeature(Feature.POSSESSIVE, Boolean.TRUE);
				sentence.setFeature(Feature.TENSE, Tense.PRESENT);
				sentence.setObject(conjugatingobjects);
				sentenseclause.add(sentence);
				uniquepredicate.add(processpredicate(t.getPredicate().toString()));
			} else {
				SPhraseSpec sentencealreadypresent = sentenseclause.get(sentenseclause.size() - 1);
				CoordinatedPhraseElement conjugatingobjects = (CoordinatedPhraseElement) sentencealreadypresent
						.getObject();
				NLGElement data = nlgFactory.createStringElement(processobject(t.getObject().toString()));
				conjugatingobjects.addCoordinate(data);
				SPhraseSpec sentencenew = nlgFactory.createClause();
				sentencenew.setFeature(Feature.NUMBER, NumberAgreement.PLURAL);
				sentencenew.setFeature(Feature.RAISE_SPECIFIER, Boolean.TRUE);
				sentencenew.setFeature(Feature.TENSE, Tense.PRESENT);
				sentencenew.setFeature(Feature.POSSESSIVE, Boolean.TRUE);
				sentencenew.setSubject(sentencealreadypresent.getSubject());
				NLGElement verb = sentencealreadypresent.getVerb();
				NPPhraseSpec verbnoun = nlgFactory.createNounPhrase(verb);
				verbnoun.setFeature(Feature.NUMBER, NumberAgreement.PLURAL);
				sentencenew.setVerb(verbnoun);
				if (!"educated at".equalsIgnoreCase(processpredicate(t.getPredicate().toString()))
						&& !"influenced by".equalsIgnoreCase(processpredicate(t.getPredicate().toString()))
						&& !"member of".equalsIgnoreCase(processpredicate(t.getPredicate().toString()))) {
					if (!flag) {
						conjugatingobjects.setFeature(InternalFeature.SPECIFIER, "are");
					} else {
						conjugatingobjects.setFeature(InternalFeature.SPECIFIER, "a");
					}
				}
				conjugatingobjects.setFeature(Feature.RAISE_SPECIFIER, Boolean.TRUE);
				sentencenew.setObject(conjugatingobjects);
				sentenseclause.set(sentenseclause.size() - 1, sentencenew);
			}
		}

		for (SPhraseSpec s : sentenseclause) {
			System.out.println(realiser.realiseSentence(s));
			text += " "+realiser.realiseSentence(s);
		}

		return text;

	}

	public String processobject(String input) {

		return input.replaceAll("\"", "").replace("_", " ");
	}

	public String processDateLiteral(String input) {

		return input.replaceAll("^^http://www.w3.org/2001/XMLSchema#date", "").replaceAll("\"", "");
	}

	public boolean isDate(String input) {
		boolean flag = false;
		if (input.contains("http://www.w3.org/2001/XMLSchema#date")) {
			flag = true;
		}

		return flag;
	}

	public String processpredicate(String Predicate) {
		String pred = Predicate.replaceAll("_", " ").replaceAll("\"", "").replace("@", "");
		if (pred.equalsIgnoreCase("instance of")) {
			pred = "is";
		} else if (pred.equalsIgnoreCase("award received")) {
			pred = "received awards";
		}
		return pred;
	}

	/**
	 * Return a textual representation for the given triple.
	 *
	 * @param t
	 *            the triple to convert
	 * @param negated
	 *            if phrase is negated
	 * @return the textual representation
	 */
	public String convert(Triple t, boolean negated) {
		NLGElement phrase = convertToPhrase(t, negated);
		String text;
		if (returnAsSentence) {
			text = realiser.realiseSentence(phrase);
		} else {
			text = realiser.realise(phrase).getRealisation();
		}
		return text;
	}

	/**
	 * Return a textual representation for the given triples. Currently we
	 * assume that all triples have the same subject!
	 * 
	 * @param triples
	 *            the triples to convert
	 * @return the textual representation
	 */
	public String convert(List<Triple> triples) {
		// combine with conjunction
		CoordinatedPhraseElement typesConjunction = nlgFactory.createCoordinatedPhrase();

		// separate type triples from others
		List<Triple> typeTriples = triples.stream().filter(t -> t.predicateMatches(RDF.type.asNode()))
				.collect(Collectors.toList());
		List<Triple> otherTriples = ListUtils.subtract(triples, typeTriples);

		// convert the type triples
		List<SPhraseSpec> typePhrases = convertToPhrases(typeTriples);

		// if there is more than one type, we combine them into a single clause
		if (typePhrases.size() > 1) {
			// combine all objects in a coordinated phrase
			CoordinatedPhraseElement combinedObject = nlgFactory.createCoordinatedPhrase();

			// the last 2 phrases are combined via 'as well as'
			if (useAsWellAsCoordination) {
				SPhraseSpec phrase1 = typePhrases.remove(typePhrases.size() - 1);
				SPhraseSpec phrase2 = typePhrases.get(typePhrases.size() - 1);
				// combine all objects in a coordinated phrase
				CoordinatedPhraseElement combinedLastTwoObjects = nlgFactory
						.createCoordinatedPhrase(phrase1.getObject(), phrase2.getObject());
				combinedLastTwoObjects.setConjunction("as well as");
				combinedLastTwoObjects.setFeature(Feature.RAISE_SPECIFIER, false);
				combinedLastTwoObjects.setFeature(InternalFeature.SPECIFIER, "a");
				phrase2.setObject(combinedLastTwoObjects);
			}

			Iterator<SPhraseSpec> iterator = typePhrases.iterator();
			// pick first phrase as representative
			SPhraseSpec representative = iterator.next();
			combinedObject.addCoordinate(representative.getObject());

			while (iterator.hasNext()) {
				SPhraseSpec phrase = iterator.next();
				NLGElement object = phrase.getObject();
				combinedObject.addCoordinate(object);
			}

			combinedObject.setFeature(Feature.RAISE_SPECIFIER, true);
			// set the coordinated phrase as the object
			representative.setObject(combinedObject);
			// return a single phrase
			typePhrases = Lists.newArrayList(representative);
		}
		for (SPhraseSpec phrase : typePhrases) {
			typesConjunction.addCoordinate(phrase);
		}

		// convert the other triples
		CoordinatedPhraseElement othersConjunction = nlgFactory.createCoordinatedPhrase();
		List<SPhraseSpec> otherPhrases = convertToPhrases(otherTriples);
		List<SPhraseSpec> otherphraseswithout_be_verb = new ArrayList<SPhraseSpec>();
		List<DocumentElement> sentences1 = new ArrayList();
		HashMap<NLGElement, List<SPhraseSpec>> otherphraseswithout_be_verbtemp = new HashMap<NLGElement, List<SPhraseSpec>>();
		// we have to keep one triple with subject if we have no type triples
		SPhraseSpec forcomparison = nlgFactory.createClause();
		forcomparison.setVerb("be");
		boolean flag = false;
		if (typeTriples.isEmpty()) {
			if (otherPhrases.size() == 2) {

				if ((otherPhrases.get(0).getVerb().equals(otherPhrases.get(1).getVerb()))
						&& (!otherPhrases.get(0).getVerb().equals(forcomparison.getVerb()))) {
					NPPhraseSpec determiner = nlgFactory.createNounPhrase("both");
					NPPhraseSpec firstclause = nlgFactory.createNounPhrase(otherPhrases.get(0).getObject());
					firstclause.setDeterminer(determiner);

					CoordinatedPhraseElement temp = nlgFactory.createCoordinatedPhrase(firstclause,
							otherPhrases.get(1).getObject());
					SPhraseSpec combinedsentence = nlgFactory.createClause();
					combinedsentence.setSubject(otherPhrases.get(0).getSubject());
					combinedsentence.setVerb(otherPhrases.get(0).getVerb());
					combinedsentence.setObject(temp);
					othersConjunction.addCoordinate(combinedsentence);
					otherPhrases.clear();

				} else {
					othersConjunction.addCoordinate(otherPhrases.remove(0));
				}

			} else {
				otherphraseswithout_be_verb = otherPhrases.stream()
						.filter(t -> !t.getVerb().equals(forcomparison.getVerb())).collect(Collectors.toList());
				List<NLGElement> verbs = new ArrayList<NLGElement>();

				for (SPhraseSpec s : otherphraseswithout_be_verb) {
					verbs.add(s.getVerb());
				}
				Set<String> uniques = new HashSet<String>();
				Set<NLGElement> duplicateverbs = new HashSet<NLGElement>();
				Set<String> duplicateverbstemp = new HashSet<String>();
				for (NLGElement p : verbs) {
					if (!uniques.add(p.toString())) {
						if (!duplicateverbstemp.contains(p.toString())) {
							duplicateverbstemp.add(p.toString());
							duplicateverbs.add(p);
						}
					}
				}
				for (NLGElement p : duplicateverbs) {
					otherphraseswithout_be_verbtemp.put(p, new ArrayList<SPhraseSpec>());
				}

				for (NLGElement p : duplicateverbs) {
					for (SPhraseSpec s : otherphraseswithout_be_verb) {
						if (p.equals(s.getVerb())) {
							otherphraseswithout_be_verbtemp.get(p).add(s);
						}
					}
				}
				List<SPhraseSpec> temp = new ArrayList<SPhraseSpec>();
				for (NLGElement p : duplicateverbs) {
					for (SPhraseSpec s : otherphraseswithout_be_verbtemp.get(p)) {
						temp.add(s);
					}

				}

				// otherphraseswithout_be_verb=ListUtils.subtract(otherphraseswithout_be_verb,otherphraseswithout_be_verbtemp);
				// otherphraseswithout_be_verb=ListUtils.subtract(otherphraseswithout_be_verb,temp);
				otherPhrases = ListUtils.subtract(otherPhrases, temp);
				if (!otherPhrases.isEmpty()) {
					flag = true;
					othersConjunction.addCoordinate(otherPhrases.remove(0));
				}
			}
		}
		// make subject pronominal, i.e. -> he/she/it
		if (!otherPhrases.isEmpty()) {
			otherPhrases.stream().forEach(p -> asPronoun(p.getSubject()));
			for (SPhraseSpec phrase : otherPhrases) {
				othersConjunction.addCoordinate(phrase);
			}
			for (NLGElement pi : otherphraseswithout_be_verbtemp.keySet()) {
				CoordinatedPhraseElement joiningsubparts = nlgFactory.createCoordinatedPhrase();
				if (!otherphraseswithout_be_verbtemp.get(pi).isEmpty()) {
					otherphraseswithout_be_verbtemp.get(pi).stream().forEach(p -> asPronoun(p.getSubject()));

					for (SPhraseSpec phrase : otherphraseswithout_be_verbtemp.get(pi)) {
						joiningsubparts.addCoordinate(phrase.getObject());
					}
				}
				if (!otherphraseswithout_be_verbtemp.get(pi).isEmpty()) {
					SPhraseSpec sentenceclause = nlgFactory.createClause();
					sentenceclause.setSubject(otherphraseswithout_be_verbtemp.get(pi).get(0).getSubject());
					sentenceclause.setVerb(otherphraseswithout_be_verbtemp.get(pi).get(0).getVerb());
					sentenceclause.setObject(joiningsubparts);
					othersConjunction.addCoordinate(sentenceclause);
				}
			}

		} else {
			int i = 0;
			for (NLGElement pi : otherphraseswithout_be_verbtemp.keySet()) {
				i++;
				CoordinatedPhraseElement othersConjunctiontemp = nlgFactory.createCoordinatedPhrase();
				CoordinatedPhraseElement joiningsubparts = nlgFactory.createCoordinatedPhrase();
				if (!otherphraseswithout_be_verbtemp.get(pi).isEmpty()) {
					if (flag) {
						otherphraseswithout_be_verbtemp.get(pi).stream().forEach(p -> asPronoun(p.getSubject()));
					}

					for (SPhraseSpec phrase : otherphraseswithout_be_verbtemp.get(pi)) {
						joiningsubparts.addCoordinate(phrase.getObject());
					}
					SPhraseSpec sentenceclause = nlgFactory.createClause();
					sentenceclause.setSubject(otherphraseswithout_be_verbtemp.get(pi).get(0).getSubject());
					sentenceclause.setVerb(otherphraseswithout_be_verbtemp.get(pi).get(0).getVerb());
					sentenceclause.setObject(joiningsubparts);
					othersConjunctiontemp.addCoordinate(sentenceclause);
					sentences1.add(nlgFactory.createSentence(othersConjunctiontemp));
				}
				if (i != 0) {
					flag = true;
				}

			}
		}
		List<DocumentElement> sentences = new ArrayList();
		if (!typeTriples.isEmpty()) {
			sentences.add(nlgFactory.createSentence(typesConjunction));
		}

		if (!otherTriples.isEmpty()) {
			sentences.add(nlgFactory.createSentence(othersConjunction));
		}

		if (!sentences1.isEmpty()) {
			for (DocumentElement e : sentences1) {
				sentences.add(e);
			}
		}

		DocumentElement paragraph = nlgFactory.createParagraph(sentences);
		String realisation = realiser.realise(paragraph).getRealisation().trim();

		return realisation;
	}

	private void asPronoun(NLGElement el) {
		if (el.hasFeature(InternalFeature.SPECIFIER)) {
			NLGElement specifier = el.getFeatureAsElement(InternalFeature.SPECIFIER);
			if (specifier.hasFeature(Feature.POSSESSIVE)) {
				specifier.setFeature(Feature.PRONOMINAL, true);
			}
		} else {
			el.setFeature(Feature.PRONOMINAL, true);
		}
	}

	/**
	 * Convert a triple into a phrase object
	 * 
	 * @param t
	 *            the triple
	 * @return the phrase
	 */
	public SPhraseSpec convertToPhrase(Triple t) {
		return convertToPhrase(t, false);
	}

	/**
	 * Convert a triple into a phrase object
	 * 
	 * @param t
	 *            the triple
	 * @return the phrase
	 */
	public SPhraseSpec convertToPhrase(Triple t, boolean negated) {
		return convertToPhrase(t, negated, false);
	}
	
	public String getinfo(Triple t) {
		org.apache.jena.graph.Node subject = t.getSubject();
		org.apache.jena.graph.Node predicate = t.getPredicate();
		org.apache.jena.graph.Node object = t.getObject();
		String service = "http://dbpedia.org/sparql";
		String Object="";
		try {
			String q;
			String pred=predicate.toString().replace("@", "");
			q = "SELECT ?o where { <" + subject.toString() + "> <"+pred+"> ?o.}";
			QueryExecution qe = org.apache.jena.query.QueryExecutionFactory.sparqlService(service, q);
			ResultSet results = qe.execSelect();
			while (results.hasNext()) {
				RDFNode node = results.next().get("o");
				Object=node.toString().replace("^^http://www.w3.org/2001/XMLSchema#date", "");
				
				//TODO
				
			}
			
			
		}catch (Exception e) {
			e.printStackTrace();
		}
		
		return Object;
	}

	/**
	 * Convert a triple into a phrase object
	 *
	 * @param t
	 *            the triple
	 * @param negated
	 *            if phrase is negated
	 * @param reverse
	 *            whether subject and object should be changed during
	 *            verbalization
	 * @return the phrase
	 */
	public SPhraseSpec convertToPhrase(Triple t, boolean negated, boolean reverse) {
		logger.debug("Verbalizing triple " + t);
		System.out.println("Verbalizing triple " + t);
		SPhraseSpec p = nlgFactory.createClause();

		Node subject = t.getSubject();
		Node predicate = t.getPredicate();
		Node object = t.getObject();
		if(object.toString().equals("http://dbpedia.org/resource/")) {
			object=NodeFactory.createURI(getinfo(t));
		}

		// process predicate
		// start with variables
		if (predicate.isVariable()) {
			// if subject is variable then use variable label, else generate
			// textual representation
			// first get the string representation for the subject
			NLGElement subjectElement = processSubject(subject);
			p.setSubject(subjectElement);

			// predicate is variable, thus simply use variable label
			p.setVerb("be related via " + predicate.toString() + " to");

			// then process the object
			NLGElement objectElement = processObject(object, false);
			p.setObject(objectElement);
		} // more interesting case. Predicate is not a variable
			// then check for noun and verb. If the predicate is a noun or a
			// verb, then
			// use possessive or verbal form, else simply get the boa pattern
		else {
			// check if object is class
			boolean objectIsClass = predicate.matches(RDF.type.asNode());

			// first get the string representation for the subject
			NLGElement subjectElement = processSubject(subject);

			// then process the object
			NPPhraseSpec objectElement = processObject(object, objectIsClass);

			// handle the predicate
			PropertyVerbalization propertyVerbalization = pp.verbalize(predicate.getURI());
			String predicateAsString = propertyVerbalization.getVerbalizationText();

			// if the object is a class we generate 'SUBJECT be a(n) OBJECT'
			if (objectIsClass) {
				p.setSubject(subjectElement);
				p.setVerb("be");
				objectElement.setSpecifier("a");
				p.setObject(objectElement);
			} else {
				// get the lexicalization type of the predicate

				PropertyVerbalizationType type;
				if (predicate.matches(RDFS.label.asNode())) {
					type = PropertyVerbalizationType.NOUN;
				} else {
					type = propertyVerbalization.getVerbalizationType();
				}

				/*-
				 * if the predicate is a noun we generate a possessive form, i.e. 'SUBJECT'S PREDICATE be OBJECT'
				 */
				if (type == PropertyVerbalizationType.NOUN) {
					// subject is a noun with possessive feature
					// NLGElement subjectWord =
					// nlgFactory.createInflectedWord(realiser.realise(subjectElement).getRealisation(),
					// LexicalCategory.NOUN);
					// subjectWord.setFeature(LexicalFeature.PROPER, true);
					// subjectElement =
					// nlgFactory.createNounPhrase(subjectWord);
					subjectElement.setFeature(Feature.POSSESSIVE, true);
					// build the noun phrase for the predicate
					NPPhraseSpec predicateNounPhrase = nlgFactory
							.createNounPhrase(PlingStemmer.stem(predicateAsString));
					// set the possessive subject as specifier
					predicateNounPhrase.setFeature(InternalFeature.SPECIFIER, subjectElement);

					// check if object is a string literal with a language tag
					if (considerLiteralLanguage) {
						if (object.isLiteral() && object.getLiteralLanguage() != null
								&& !object.getLiteralLanguage().isEmpty()) {
							String languageTag = object.getLiteralLanguage();
							String language = Locale.forLanguageTag(languageTag).getDisplayLanguage(Locale.ROOT);
							predicateNounPhrase.addPreModifier(language);
						}
					}

					p.setSubject(predicateNounPhrase);

					// we use 'be' as the new predicate
					p.setVerb("be");

					// add object
					p.setObject(objectElement);

					// check if we have to use the plural form
					// simple heuristic: OBJECT is variable and predicate is of
					// type owl:FunctionalProperty or rdfs:range is xsd:boolean
					boolean isPlural = determinePluralForm && usePluralForm(t);
					predicateNounPhrase.setPlural(isPlural);
					p.setPlural(isPlural);

					// check if we reverse the triple representation
					if (reverse) {
						subjectElement.setFeature(Feature.POSSESSIVE, false);
						p.setSubject(subjectElement);
						p.setVerbPhrase(nlgFactory.createVerbPhrase("be " + predicateAsString + " of"));
						p.setObject(objectElement);
					}
				} // if the predicate is a verb
				else if (type == PropertyVerbalizationType.VERB) {
					p.setSubject(subjectElement);
					p.setVerb(pp.getInfinitiveForm(predicateAsString));
					p.setObject(objectElement);
					p.setFeature(Feature.TENSE, propertyVerbalization.getTense());
				} // in other cases, use the BOA pattern
				else {

					List<org.aksw.triple2nl.nlp.relation.Pattern> l = BoaPatternSelector
							.getNaturalLanguageRepresentation(predicate.toString(), 1);
					if (l.size() > 0) {
						String boaPattern = l.get(0).naturalLanguageRepresentation;
						// range before domain
						if (boaPattern.startsWith("?R?")) {
							p.setSubject(subjectElement);
							p.setObject(objectElement);
						} else {
							p.setObject(subjectElement);
							p.setSubject(objectElement);
						}
						p.setVerb(BoaPatternSelector.getNaturalLanguageRepresentation(predicate.toString(), 1)
								.get(0).naturalLanguageRepresentationWithoutVariables);
					} // last resort, i.e., no BOA pattern found
					else {
						p.setSubject(subjectElement);
						p.setVerb("be related via \"" + predicateAsString + "\" to");
						p.setObject(objectElement);
					}
				}
			}
		}
		// check if the meaning of the triple is it's negation, which holds for
		// boolean properties with FALSE as value
		if (!negated) {
			// check if object is boolean literal
			if (object.isLiteral() && object.getLiteralDatatype() != null
					&& object.getLiteralDatatype().equals(XSDDatatype.XSDboolean)) {
				// omit the object
				p.setObject(null);

				negated = !(boolean) object.getLiteralValue();

			}
		}

		// set negation
		if (negated) {
			p.setFeature(Feature.NEGATED, negated);
		}

		// set present time as tense
		// p.setFeature(Feature.TENSE, Tense.PRESENT);
		// System.out.println(realiser.realise(p));
		return p;
	}

	/**
	 * Converts a collection of triples into a list of phrases.
	 *
	 * @param triples
	 *            the triples
	 * @return a list of phrases
	 */
	public List<SPhraseSpec> convertToPhrases(Collection<Triple> triples) {
		List<SPhraseSpec> phrases = new ArrayList<>();
		for (Triple triple : triples) {
			phrases.add(convertToPhrase(triple));
		}
		return phrases;
	}

	/**
	 * Whether to encapsulate the value of string literals in "".
	 * {@see LiteralConverter#setEncapsulateStringLiterals(boolean)}
	 * 
	 * @param encapsulateStringLiterals
	 *            TRUE if string has to be wrapped in "", otherwise FALSE
	 */
	public void setEncapsulateStringLiterals(boolean encapsulateStringLiterals) {
		this.literalConverter.setEncapsulateStringLiterals(encapsulateStringLiterals);
	}

	/**
	 * @param determinePluralForm
	 *            the determinePluralForm to set
	 */
	public void setDeterminePluralForm(boolean determinePluralForm) {
		this.determinePluralForm = determinePluralForm;
	}

	/**
	 * @param considerLiteralLanguage
	 *            the considerLiteralLanguage to set
	 */
	public void setConsiderLiteralLanguage(boolean considerLiteralLanguage) {
		this.considerLiteralLanguage = considerLiteralLanguage;
	}

	private boolean usePluralForm(Triple triple) {
		return triple.getObject().isVariable()
				&& !(reasoner.isFunctional(new OWLObjectPropertyImpl(IRI.create(triple.getPredicate().getURI())))
						|| reasoner.getRange(new OWLDataPropertyImpl(IRI.create(triple.getPredicate().getURI())))
								.asOWLDatatype().getIRI().equals(OWL2Datatype.XSD_BOOLEAN.getIRI()));
	}

	/**
	 * @param returnAsSentence
	 *            whether the style of the returned result is a proper English
	 *            sentence or just a phrase
	 */
	public void setReturnAsSentence(boolean returnAsSentence) {
		this.returnAsSentence = returnAsSentence;
	}

	/**
	 * @param useGenderInformation
	 *            whether to use the gender information about a resource
	 */
	public void setUseGenderInformation(boolean useGenderInformation) {
		this.useGenderInformation = useGenderInformation;
	}

	public void setGenderDetector(GenderDetector genderDetector) {
		this.genderDetector = genderDetector;
	}

	/**
	 * Process the node and return an NLG element that contains the textual
	 * representation. The output depends on the node type, i.e. variable, URI
	 * or literal.
	 * 
	 * @param node
	 *            the node to process
	 * @return the NLG element containing the textual representation of the node
	 */
	public NLGElement processNode(Node node) {
		NLGElement element;
		if (node.isVariable()) {
			element = processVarNode(node);
		} else if (node.isURI()) {
			element = processResourceNode(node);
		} else if (node.isLiteral()) {
			element = processLiteralNode(node);
		} else {
			throw new UnsupportedOperationException("Can not convert blank node.");
		}
		return element;
	}

	/**
	 * Converts the node that is supposed to represent a class in the knowledge
	 * base into an NL phrase.
	 * 
	 * @param node
	 *            the node
	 * @param plural
	 *            whether the plural form should be used
	 * @return the NL phrase
	 */
	public NPPhraseSpec processClassNode(Node node, boolean plural) {
		NPPhraseSpec object;
		if (node.equals(OWL.Thing.asNode())) {
			object = nlgFactory.createNounPhrase(GenericType.ENTITY.getNlr());
		} else if (node.equals(RDFS.Literal.asNode())) {
			object = nlgFactory.createNounPhrase(GenericType.VALUE.getNlr());
		} else if (node.equals(RDF.Property.asNode())) {
			object = nlgFactory.createNounPhrase(GenericType.RELATION.getNlr());
		} else if (node.equals(RDF.type.asNode())) {
			object = nlgFactory.createNounPhrase(GenericType.TYPE.getNlr());
		} else {
			String label = uriConverter.convert(node.getURI());
			if (label != null) {
				// get the singular form
				label = PlingStemmer.stem(label);
				// we assume that classes are always used in lower case format
				label = label.toLowerCase();
				object = nlgFactory.createNounPhrase(nlgFactory.createWord(label, LexicalCategory.NOUN));
			} else {
				object = nlgFactory.createNounPhrase(GenericType.ENTITY.getNlr());
			}

		}
		// set plural form
		object.setPlural(plural);
		return object;
	}

	public NPPhraseSpec processVarNode(Node varNode) {
		return nlgFactory.createNounPhrase(nlgFactory.createWord(varNode.toString(), LexicalCategory.NOUN));
	}

	public NPPhraseSpec processLiteralNode(Node node) {
		LiteralLabel lit = node.getLiteral();
		// convert the literal
		String literalText = literalConverter.convert(lit);
		NPPhraseSpec np = nlgFactory.createNounPhrase(nlgFactory.createWord(literalText, LexicalCategory.NOUN));
		np.setPlural(literalConverter.isPlural(lit));
		return np;
	}

	public NPPhraseSpec processResourceNode(Node node) {
		// get string from URI
		System.out.println("Node is "+node);
		String s = uriConverter.convert(node.getURI());

		// create word
		NLGElement word = nlgFactory.createWord(s, LexicalCategory.NOUN);

		// add gender information if enabled
		if (useGenderInformation) {
			Gender gender = genderDetector.getGender(s);

			if (gender == Gender.FEMALE) {
				word.setFeature(LexicalFeature.GENDER, simplenlg.features.Gender.FEMININE);
			} else if (gender == Gender.MALE) {
				word.setFeature(LexicalFeature.GENDER, simplenlg.features.Gender.MASCULINE);
			}
		}

		// should be a proper noun, thus, will not be pluralized by morphology
		word.setFeature(LexicalFeature.PROPER, true);

		// wrap in NP
		NPPhraseSpec np = nlgFactory.createNounPhrase(word);
		return np;
	}

	private NLGElement processSubject(Node subject) {
		NLGElement element;
		if (subject.isVariable()) {
			element = processVarNode(subject);
		} else if (subject.isURI()) {
			element = processResourceNode(subject);
		} else if (subject.isLiteral()) {
			element = processLiteralNode(subject);
		} else {
			throw new UnsupportedOperationException("Can not convert " + subject);
		}
		return element;
	}

	private NPPhraseSpec processObject(Node object, boolean isClass) {
		NPPhraseSpec element;
		if (object.isVariable()) {
			element = processVarNode(object);
		} else if (object.isLiteral()) {
			element = processLiteralNode(object);
		} else if (object.isURI()) {
			if (isClass) {
				element = processClassNode(object, false);
			} else {
				element = processResourceNode(object);
			}
		} else {
			throw new IllegalArgumentException("Can not convert blank node " + object + ".");
		}
		return element;
	}

	/**
	 * Takes a URI and returns a noun phrase for it
	 * 
	 * @param uri
	 *            the URI to convert
	 * @param plural
	 *            whether it is in plural form
	 * @param isClass
	 *            if URI is supposed to be a class
	 * @return the noun phrase
	 */
	public NPPhraseSpec getNPPhrase(String uri, boolean plural, boolean isClass) {
		NPPhraseSpec object;
		if (uri.equals(OWL.Thing.getURI())) {
			object = nlgFactory.createNounPhrase(GenericType.ENTITY.getNlr());
		} else if (uri.equals(RDFS.Literal.getURI())) {
			object = nlgFactory.createNounPhrase(GenericType.VALUE.getNlr());
		} else if (uri.equals(RDF.Property.getURI())) {
			object = nlgFactory.createNounPhrase(GenericType.RELATION.getNlr());
		} else if (uri.equals(RDF.type.getURI())) {
			object = nlgFactory.createNounPhrase(GenericType.TYPE.getNlr());
		} else {
			String label = uriConverter.convert(uri);
			if (label != null) {
				if (isClass) {
					// get the singular form
					label = PlingStemmer.stem(label);
					// we assume that classes are always used in lower case
					// format
					label = label.toLowerCase();
				}
				object = nlgFactory.createNounPhrase(nlgFactory.createWord(label, LexicalCategory.NOUN));
			} else {
				object = nlgFactory.createNounPhrase(GenericType.ENTITY.getNlr());
			}

		}
		object.setPlural(plural);

		return object;
	}

	public static void main(String[] args) throws Exception {

		System.out.println(new TripleConverter()
				.convert(Triple.create(NodeFactory.createURI("http://dbpedia.org/resource/Albert_Einstein"),
						NodeFactory.createURI("http://dbpedia.org/ontology/birthPlace"),
						NodeFactory.createURI("http://dbpedia.org/resource/Ulm"))));

		System.out.println(new TripleConverter()
				.convert(Triple.create(NodeFactory.createURI("http://dbpedia.org/resource/Albert_Einstein"),
						NodeFactory.createURI("http://dbpedia.org/ontology/isHardWorking"),
						NodeFactory.createLiteral("false", XSDDatatype.XSDboolean))));
	}

}
