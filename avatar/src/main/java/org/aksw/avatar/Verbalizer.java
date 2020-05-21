/*
 * #%L
 * AVATAR
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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.avatar;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import org.aksw.avatar.clustering.BorderFlowX;
import org.aksw.avatar.clustering.Node;
import org.aksw.avatar.clustering.WeightedGraph;
import org.aksw.avatar.clustering.hardening.HardeningFactory;
import org.aksw.avatar.clustering.hardening.HardeningFactory.HardeningType;
import org.aksw.avatar.dataset.CachedDatasetBasedGraphGenerator;
import org.aksw.avatar.dataset.DatasetBasedGraphGenerator;
import org.aksw.avatar.dataset.DatasetBasedGraphGenerator.Cooccurrence;
import org.aksw.avatar.exceptions.NoGraphAvailableException;
import org.aksw.avatar.gender.Gender;
import org.aksw.avatar.gender.LexiconBasedGenderDetector;
import org.aksw.avatar.gender.TypeAwareGenderDetector;
import org.aksw.avatar.rules.DateLiteralFilter;
import org.aksw.avatar.rules.NumericLiteralFilter;
import org.aksw.avatar.rules.ObjectMergeRule;
import org.aksw.avatar.rules.PredicateMergeRule;
import org.aksw.avatar.rules.SubjectMergeRule;
import org.aksw.jena_sparql_api.cache.h2.CacheUtilsH2;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.impl.LiteralLabel;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.utilities.MapUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLIndividual;

import simplenlg.features.Feature;
import simplenlg.features.InternalFeature;
import simplenlg.features.LexicalFeature;
import simplenlg.features.NumberAgreement;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.NLGElement;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;
import uk.ac.manchester.cs.owl.owlapi.OWLClassImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;

/**
 * A verbalizer for triples without variables.
 *
 * @author ngonga
 */
public class Verbalizer {

	private static final List<XSDDatatype> dateTypes = Lists.newArrayList(XSDDatatype.XSDdateTime, XSDDatatype.XSDdate,
			XSDDatatype.XSDgYearMonth, XSDDatatype.XSDgYear, XSDDatatype.XSDgMonth, XSDDatatype.XSDgMonthDay);

	private static final Logger logger = Logger.getLogger(Verbalizer.class.getName());

	private static final double DEFAULT_THRESHOLD = 0.35;
	private static final Cooccurrence DEFAULT_COOCCURRENCE_TYPE = Cooccurrence.PROPERTIES;
	private static final HardeningType DEFAULT_HARDENING_TYPE = HardeningType.SMALLEST;

	public SimpleNLGwithPostprocessing nlg;
	public static Document doc = null;
	public static Document doc2 = null;
	SparqlEndpoint endpoint;
	String language = "en";
	protected Realiser realiser;
	Map<Resource, String> labels;
	NumericLiteralFilter litFilter;
	TypeAwareGenderDetector gender;
	public Map<Resource, Collection<Triple>> resource2Triples;
	private QueryExecutionFactory qef;
	private String cacheDirectory = "cache/sparql";
	PredicateMergeRule pr;
	ObjectMergeRule or;
	SubjectMergeRule sr;
	public DatasetBasedGraphGenerator graphGenerator;
	int maxShownValuesPerProperty = 7;
	boolean omitContentInBrackets = true;

	public Verbalizer(QueryExecutionFactory qef, String cacheDirectory, String wordnetDirectory) {
		this.qef = qef;

		nlg = new SimpleNLGwithPostprocessing(qef, cacheDirectory, null);
		labels = new HashMap<Resource, String>();
		litFilter = new NumericLiteralFilter(qef, cacheDirectory);
		realiser = nlg.realiser;

		pr = new PredicateMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
		or = new ObjectMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);
		sr = new SubjectMergeRule(nlg.lexicon, nlg.nlgFactory, nlg.realiser);

		gender = new TypeAwareGenderDetector(qef, new LexiconBasedGenderDetector());

		graphGenerator = new CachedDatasetBasedGraphGenerator(qef, cacheDirectory);
	}

	public Verbalizer(SparqlEndpoint endpoint, String cacheDirectory, String wordnetDirectory) {
		this(new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs()),
				cacheDirectory, wordnetDirectory);
	}

	/**
	 * @param blacklist
	 *            a blacklist of properties that are omitted when building the
	 *            summary
	 */
	public void setPropertiesBlacklist(Set<String> blacklist) {
		graphGenerator.setPropertiesBlacklist(blacklist);
	}

	/**
	 * @param personTypes
	 *            the personTypes to set
	 */
	public void setPersonTypes(Set<String> personTypes) {
		gender.setPersonTypes(personTypes);
	}

	/**
	 * @param omitContentInBrackets
	 *            the omitContentInBrackets to set
	 */
	public void setOmitContentInBrackets(boolean omitContentInBrackets) {
		this.omitContentInBrackets = omitContentInBrackets;
	}

	/**
	 * Gets all triples for resource r and property p. If outgoing is true it
	 * returns all triples with <r,p,o>, else <s,p,r>
	 *
	 * @param r
	 *            the resource
	 * @param p
	 *            the property
	 * @param outgoing
	 *            whether to get outgoing or ingoing triples
	 * @return A set of triples
	 */
	public String processnodeSubject(String nodename) {
		String processedname = "";
		String segments[] = nodename.split("/");
		processedname = segments[segments.length - 1];

		return processedname;
	}

	public String processnode(String nodename) {
		String processedname = "";
		String segments[] = nodename.split("/");
		processedname = segments[segments.length - 1].replaceAll("_", "");

		return processedname;
	}

	public String processnodewithdate(String nodename) {
		String processedname = "";
		processedname = nodename.substring(nodename.indexOf("\"") + 1, nodename.lastIndexOf("\""));

		return processedname;
	}

	public String getcorpusfromwikipedia(String Subject) {
		String corpus = "";
		try {
			doc = Jsoup.connect("https://en.wikipedia.org/wiki/" + Subject).get();
			corpus = getPlainText(doc);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return corpus;
	}

	public String getcorpusfromwikipedia2(String Subject) {
		String corpus = "";
		try {
			doc2 = Jsoup.connect("https://en.wikipedia.org/wiki/" + Subject).get();
			corpus = getPlainText(doc2);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return corpus;
	}

	public static String getPlainText(Document doc) {
		Elements ps = doc.select("p");
		return ps.text();
	}

	public boolean validatetriples(String Subject, String Predicate, String Object) {
		boolean flag = false;
		Elements infobox = doc.select("table[class*=infobox]");
		if (ListUtil.isNotEmpty(infobox)) {
			Elements infoboxRows = infobox.get(0).select("tbody").select("tr");
			for (Element e : infoboxRows) {
				String line = e.text();
				if (line.startsWith("Born") && line.contains(Object)) {
					flag = true;
				}

			}

		}

		return flag;
	}

	public boolean validatetriples2(String Subject, String Predicate, String Object) {
		boolean flag = false;
		Elements infobox = doc2.select("table[class*=infobox]");
		if (ListUtil.isNotEmpty(infobox)) {
			Elements infoboxRows = infobox.get(0).select("tbody").select("tr");
			for (Element e : infoboxRows) {
				String line = e.text();
				if (line.startsWith("State") && !((line.replaceAll("State", "")).isEmpty())) {
					// System.out.println("\n"+line);
					flag = true;
				}

				if (line.startsWith("District") && !line.contains(Object)) {
					flag = false;
				}

			}

		}

		return flag;
	}

	public Set<Triple> getTriples(Resource r, Property p, boolean outgoing) {
		Set<Triple> result = new HashSet<Triple>();
		try {
			String q;
			if (outgoing) {
				q = "SELECT ?o where { <" + r.getURI() + "> <" + p.getURI() + "> ?o.}";
			} else {
				q = "SELECT ?o where { ?o <" + p.getURI() + "> <" + r.getURI() + ">.}";
			}
			
			q += " LIMIT " + maxShownValuesPerProperty + 1;
			QueryExecution qe = qef.createQueryExecution(q);
			ResultSet results = qe.execSelect();
			getcorpusfromwikipedia(processnodeSubject(r.asNode().toString()));
			validatetriples(processnode(r.asNode().toString()), "", "");
			if (results.hasNext()) {
				while (results.hasNext()) {
					RDFNode n = results.next().get("o");
					if ("birthDate".equals(processnode(p.asNode().toString()))
							&& validatetriples(processnodeSubject(r.asNode().toString()),
									processnode(p.asNode().toString()), processnodewithdate(n.asNode().toString()))) {
						result.add(Triple.create(r.asNode(), p.asNode(), n.asNode()));
					} else if ("birthPlace".equals(processnode(p.asNode().toString()))
							&& validatetriples(processnodeSubject(r.asNode().toString()),
									processnode(p.asNode().toString()), processnode(n.asNode().toString()))) {
						// System.out.println(p.asNode().toString()+"\n");
						getcorpusfromwikipedia2(processnode(n.asNode().toString()));
						if (validatetriples2("", "", processnode(n.asNode().toString()))) {
							result.add(Triple.create(r.asNode(), p.asNode(), n.asNode()));
						}
					} else if (!("birthDate".equals(processnode(p.asNode().toString()))
							|| "birthPlace".equals(processnode(p.asNode().toString())))) {
						result.add(Triple.create(r.asNode(), p.asNode(), n.asNode()));
					}
				}
			}
			qe.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	public Set<Node> getSummaryProperties(OWLClass cls, double threshold, String namespace,
			DatasetBasedGraphGenerator.Cooccurrence cooccurrence) {
		Set<Node> properties = new HashSet<Node>();
		WeightedGraph wg;
		try {
			wg = graphGenerator.generateGraph(cls, threshold, namespace, cooccurrence);
			return wg.getNodes().keySet();
		} catch (NoGraphAvailableException e) {
			logger.error(e.getMessage());
		}
		return null;
	}

	/**
	 * Generates the string representation of a verbalization
	 *
	 * @param properties
	 *            List of property clusters to be used for verbalization
	 * @param resource
	 *            Resource to summarize
	 * @return Textual representation
	 */
	public String realize(List<Set<Node>> properties, Resource resource, OWLClass nc) {
		List<NLGElement> elts = generateSentencesFromClusters(properties, resource, nc);
		return realize(elts);
	}

	public String realize(List<NLGElement> elts) {
		if (elts.isEmpty())
			return null;
		String realization = "";
		for (NLGElement elt : elts) {
			realization = realization + realiser.realiseSentence(elt) + " ";
		}
		return realization.substring(0, realization.length() - 1);
	}

	public List<NLGElement> generateSentencesFromClusters(List<Set<Node>> clusters, Resource resource,
			OWLClass OWLClass) {
		return generateSentencesFromClusters(clusters, resource, OWLClass, false);
	}

	/**
	 * Takes the output of the clustering for a given class and a resource.
	 * Returns the verbalization for the resource
	 *
	 * @param clusters
	 *            Output of the clustering
	 * @param resource
	 *            Resource to summarize
	 * @return List of NLGElement
	 */
	public org.apache.jena.graph.Node setDateFormat(org.apache.jena.graph.Node s) {

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String date = s.toString();
		String[] valuesInQuotes = StringUtils.substringsBetween(date, "\"", "\"");
		Date d2 = new Date();
		try {
			d2 = sdf.parse(valuesInQuotes[0]);
		} catch (Exception e) {
			System.out.println(e.toString());
		}
		String dater = "";
		try {
			dater = sdf.format(d2);
		} catch (Exception e) {
			System.out.println(e.toString());
		}
		org.apache.jena.graph.Node parseddate = NodeFactory.createLiteral(dater, XSDDatatype.XSDdate);
		return parseddate;
	}

	private boolean isDateDatatype(org.apache.jena.graph.Node node) {
		if (node.isLiteral()) {
			LiteralLabel literal = node.getLiteral();

			if (literal.getDatatype() != null && dateTypes.contains(literal.getDatatype())) {
				return true;
			}
		}
		return false;
	}

	public List<NLGElement> generateSentencesFromClusters(List<Set<Node>> clusters, Resource resource,
			OWLClass namedClass, boolean replaceSubjects) {
		List<SPhraseSpec> buffer;

		// compute the gender of the resource
		Gender g = getGender(resource);

		// get a list of possible subject replacements
		
		List<NPPhraseSpec> subjects = generateSubjects(resource, namedClass, g);

		List<NLGElement> result = new ArrayList<NLGElement>();
		Collection<Triple> allTriples = new ArrayList<Triple>();
		DateLiteralFilter dateFilter = new DateLiteralFilter();
		//
		for (Set<Node> propertySet : clusters) {

			// add up all triples for the given set of properties
			Set<Triple> triples = new HashSet<Triple>();
			buffer = new ArrayList<SPhraseSpec>();
			for (Node property : propertySet) {

				triples = getTriples(resource, ResourceFactory.createProperty(property.label), property.outgoing);
				litFilter.filter(triples);
				dateFilter.filter(triples);
				Triple array[] = new Triple[triples.size()];
				int k = 0;
				for (Triple i : triples)
					array[k++] = i;
				for (int i = 0; i < array.length; i++) {
					if (isDateDatatype(array[i].getObject())) {
						org.apache.jena.graph.Node node = setDateFormat(array[i].getObject());
						Triple t1 = Triple.create(array[i].getSubject(), array[i].getPredicate(), node);
						array[i] = t1;
					}
				}
				triples = new HashSet<Triple>(Arrays.asList(array));
				// restrict the number of shown values for the same property
				boolean subsetShown = false;

				// logger.info("triples size "+triples.size());
				if (triples.size() > maxShownValuesPerProperty) {
					triples = getSubsetToShow(triples);
					subsetShown = true;
				}
				// logger.info("subset triples size "+triples.size());
				// all share the same property, thus they can be merged
				List<SPhraseSpec> phraseSpecs = getPhraseSpecsFromTriples(triples, property.outgoing);
				buffer.addAll(or.apply(phraseSpecs, subsetShown));
				allTriples.addAll(triples);
			}
			List<NLGElement> mergedElement = sr.apply(or.apply(buffer), g);
			result.addAll(mergedElement);
		}

		resource2Triples.put(resource, allTriples);

		List<NLGElement> phrases = new ArrayList<NLGElement>();
		if (replaceSubjects) {

			for (int i = 0; i < result.size(); i++) {
				NLGElement phrase = result.get(i);
				NLGElement replacedPhrase = replaceSubject(phrase, subjects, g);
				System.out.println(realiser.realiseSentence(replacedPhrase));
				phrases.add(replacedPhrase);
			}
			return phrases;
		} else {
			return result;
		}

	}

	private Set<Triple> getSubsetToShow(Set<Triple> triples) {
		Set<Triple> triplesToShow = new HashSet<>(maxShownValuesPerProperty);
		for (Triple triple : sortByObjectPopularity(triples)) {
			// logger.info("Input triple "+triple);
			if (triplesToShow.size() < maxShownValuesPerProperty) {
				triplesToShow.add(triple);
				// logger.info("added triple "+triple);
			}
		}
		for (Triple triple : sortByObjectProminence(triples)) {
			// logger.info("Input triple "+triple);
			if (triplesToShow.size() < maxShownValuesPerProperty) {
				triplesToShow.add(triple);
				// logger.info("added triple "+triple);
			}
		}

		return triplesToShow;
	}

	/**
	 * Sorts the given triples by prominence of the triple objects.
	 * 
	 * @param triples
	 *            the triples
	 * @return a list of sorted triples
	 */
	private List<Triple> sortByObjectProminence(Set<Triple> triples) {

		List<Triple> orderedTriples = new ArrayList<>();

		// if one of the objects is a literal we do not sort
		if (triples.iterator().next().getObject().isLiteral()) {
			orderedTriples.addAll(triples);
		} else {
			// we get the prominence of the object
			Map<Triple, Integer> triple2ObjectPopularity = new HashMap<>();
			for (Triple triple : triples) {
				if (triple.getObject().isURI()) {
					String query = "SELECT (COUNT(*) AS ?cnt) WHERE {<" + triple.getObject().getURI() + "> <"
							+ triple.getPredicate().getURI() + "> ?o.}";
					QueryExecution qe = qef.createQueryExecution(query);
					try {
						ResultSet rs = qe.execSelect();
						int popularity = rs.next().getLiteral("cnt").getInt();
						triple2ObjectPopularity.put(triple, popularity);
						qe.close();
					} catch (Exception e) {
						logger.warn("Execution of SPARQL query failed: " + e.getMessage() + "\n" + query);
					}
				}
			}
			List<Entry<Triple, Integer>> sortedByValues = MapUtils.sortByValues(triple2ObjectPopularity);

			for (Entry<Triple, Integer> entry : sortedByValues) {
				Triple triple = entry.getKey();
				orderedTriples.add(triple);
			}
		}

		return orderedTriples;
	}

	/**
	 * Sorts the given triples by the popularity of the triple objects.
	 * 
	 * @param triples
	 *            the triples
	 * @return a list of sorted triples
	 */
	private List<Triple> sortByObjectPopularity(Set<Triple> triples) {
		List<Triple> orderedTriples = new ArrayList<>();

		// if one of the objects is a literal we do not sort
		if (triples.iterator().next().getObject().isLiteral()) {
			orderedTriples.addAll(triples);
		} else {
			// we get the popularity of the object
			Map<Triple, Integer> triple2ObjectPopularity = new HashMap<>();
			for (Triple triple : triples) {
				if (triple.getObject().isURI()) {
					String query = "SELECT (COUNT(*) AS ?cnt) WHERE {<" + triple.getObject().getURI() + "> ?p ?o.}";
					QueryExecution qe = qef.createQueryExecution(query);
					try {
						ResultSet rs = qe.execSelect();
						int popularity = rs.next().getLiteral("cnt").getInt();
						triple2ObjectPopularity.put(triple, popularity);
						qe.close();
					} catch (Exception e) {
						logger.warn("Execution of SPARQL query failed: " + e.getMessage() + "\n" + query);
					}
				}
			}
			List<Entry<Triple, Integer>> sortedByValues = MapUtils.sortByValues(triple2ObjectPopularity);

			for (Entry<Triple, Integer> entry : sortedByValues) {
				Triple triple = entry.getKey();
				orderedTriples.add(triple);
			}
		}

		return orderedTriples;
	}

	/**
	 * Returns the triples of the summary for the given resource.
	 * 
	 * @param resource
	 *            the resource of the summary
	 * @return a set of triples
	 */
	public Collection<Triple> getSummaryTriples(Resource resource) {
		return resource2Triples.get(resource);
	}

	/**
	 * Returns the triples of the summary for the given individual.
	 * 
	 * @param individual
	 *            the individual of the summary
	 * @return a set of triples
	 */
	public Collection<Triple> getSummaryTriples(OWLIndividual individual) {
		return getSummaryTriples(ResourceFactory.createResource(individual.toStringID()));
	}

	/**
	 * Generates sentence for a given set of triples
	 *
	 * @param triples
	 *            A set of triples
	 * @return A set of sentences representing these triples
	 */
	public List<NLGElement> generateSentencesFromTriples(Set<Triple> triples, boolean outgoing, Gender g) {
		return applyMergeRules(getPhraseSpecsFromTriples(triples, outgoing), g);
	}

	/**
	 * Generates sentence for a given set of triples
	 *
	 * @param triples
	 *            A set of triples
	 * @return A set of sentences representing these triples
	 */
	public List<SPhraseSpec> getPhraseSpecsFromTriples(Set<Triple> triples, boolean outgoing) {
		List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
		SPhraseSpec phrase;

		for (Triple t : triples) {
			phrase = generateSimplePhraseFromTriple(t, outgoing);
			phrases.add(phrase);
		}
		return phrases;
	}

	/**
	 * Generates a set of sentences by merging the sentences in the list as well
	 * as possible
	 *
	 * @param triples
	 *            List of triples
	 * @return List of sentences
	 */
	public List<NLGElement> applyMergeRules(List<SPhraseSpec> triples, Gender g) {
		List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
		phrases.addAll(triples);

		int newSize = phrases.size(), oldSize = phrases.size() + 1;

		// apply merging rules if more than one sentence to merge
		if (newSize > 1) {
			// fix point iteration for object and predicate merging
			while (newSize < oldSize) {
				oldSize = newSize;
				int orCount = or.isApplicable(phrases);
				int prCount = pr.isApplicable(phrases);
				if (prCount > 0 || orCount > 0) {
					if (prCount > orCount) {
						phrases = pr.apply(phrases);
					} else {
						phrases = or.apply(phrases);
					}
				}
				newSize = phrases.size();
			}
		}
		return sr.apply(phrases, g);
	}

	/**
	 * Generates a simple phrase for a triple
	 *
	 * @param triple
	 *            A triple
	 * @return A simple phrase
	 */
	public SPhraseSpec generateSimplePhraseFromTriple(Triple triple) {
		return nlg.getNLForTriple(triple);
	}

	public String generateSentenceFromTriples(Set<Triple> triples, boolean outgoing) {
		return nlg.getSentencefortriples(triples, outgoing);
	}

	/**
	 * Generates a simple phrase for a triple
	 *
	 * @param triple
	 *            A triple
	 * @return A simple phrase
	 */
	public SPhraseSpec generateSimplePhraseFromTriple(Triple triple, boolean outgoing) {
		return nlg.getNLForTriple(triple, outgoing);
	}

	public List<NLGElement> verbalize(OWLIndividual ind, OWLClass nc, String namespace, double threshold,
			Cooccurrence cooccurrence, HardeningType hType) {
		return verbalize(Sets.newHashSet(ind), nc, namespace, threshold, cooccurrence, hType).get(ind);
	}

	public Map<OWLIndividual, List<NLGElement>> verbalize(Set<OWLIndividual> individuals, OWLClass nc, String namespace,
			double threshold, Cooccurrence cooccurrence, HardeningType hType) {
		resource2Triples = new HashMap<Resource, Collection<Triple>>();

		// first get graph for nc
		try {
			graphGenerator.setIndiv(individuals.iterator().next());
			WeightedGraph wg = graphGenerator.generateGraph(nc, threshold, namespace, cooccurrence);
			// then cluster the graph
			BorderFlowX bf = new BorderFlowX(wg);
			Set<Set<Node>> clusters = bf.cluster();
			// then harden the results
			List<Set<Node>> sortedPropertyClusters = HardeningFactory.getHardening(hType).harden(clusters, wg);
			logger.info("Cluster = " + sortedPropertyClusters);

			Map<OWLIndividual, List<NLGElement>> verbalizations = new HashMap<OWLIndividual, List<NLGElement>>();

			
			OWLIndividual ind = individuals.iterator().next();
			List<NLGElement> result = generateSentencesFromClusters(sortedPropertyClusters,
					ResourceFactory.createResource(ind.toStringID()), nc, true);

			Triple t = Triple.create(ResourceFactory.createResource(ind.toStringID()).asNode(),
					ResourceFactory.createProperty(RDF.type.getURI()).asNode(),
					ResourceFactory.createResource(nc.toStringID()).asNode());
			Collections.reverse(result);
			// result.add(generateSimplePhraseFromTriple(t));
			Collections.reverse(result);

			verbalizations.put(ind, result);

			resource2Triples.get(ResourceFactory.createResource(ind.toStringID())).add(t);
			

			return verbalizations;
		} catch (NoGraphAvailableException e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Returns a textual summary of the given entity.
	 *
	 * @return
	 */
	public String summarize(OWLIndividual individual) {
		// compute the most specific type first
		OWLClass cls = getMostSpecificType(individual);
		return summarize(individual, cls);
	}

	/**
	 * Returns a textual summary of the given entity.
	 *
	 * @return
	 */
	public String summarize(OWLIndividual individual, OWLClass nc) {
		return getSummary(individual, nc, DEFAULT_THRESHOLD, DEFAULT_COOCCURRENCE_TYPE, DEFAULT_HARDENING_TYPE);
	}

	/**
	 * Returns a textual summary of the given entity.
	 *
	 * @return
	 */
	public String getSummary(OWLIndividual individual, OWLClass nc, double threshold, Cooccurrence cooccurrence,
			HardeningType hType) {
		List<NLGElement> elements = verbalize(individual, nc, null, threshold, cooccurrence, hType);
		String summary = getIntroSentence(individual, nc);
		summary = summary.concat(". " + realize(elements));
		summary = summary.replaceAll("\\s?\\((.*?)\\)", "");
		summary = summary.replace(" , among others,", ", among others,");
		return summary;
	}

	public String getIntroSentence(OWLIndividual individual, OWLClass nc) {
		String nationality = getNationality(individual, nc);
		String entity = individual.toString().substring(individual.toString().lastIndexOf("/") + 1,
				individual.toString().length() - 1).replaceAll("_"," ");
		String article;
		if(nationality.toLowerCase().startsWith("a") || nationality.toLowerCase().startsWith("e") || 
				nationality.toLowerCase().startsWith("i") || nationality.toLowerCase().startsWith("o")
				|| nationality.toLowerCase().startsWith("u")){
			article = "an";
		}else{
			article = "a";
		}
		if (getIsAlive(individual)) {
			return  entity + " is "+article+" "+ nationality + " "
					+ nc.toString().substring(nc.toString().lastIndexOf("/") + 1, nc.toString().length() - 1);
		} else {
			return entity + " was a " + nationality + " "
					+ nc.toString().substring(nc.toString().lastIndexOf("/") + 1, nc.toString().length() - 1)
							.toString();
		}
	}

	public String getNationality(OWLIndividual individual, OWLClass cls) {
		try {
			String q;
			q = "SELECT ?o where { " + individual.toString() + " <http://dbpedia.org/ontology/birthPlace> ?o.}";
			QueryExecution qe = qef.createQueryExecution(q);
			ResultSet results = qe.execSelect();
			while (results.hasNext()) {
				RDFNode node = results.next().get("o");
				q = "SELECT ?o where { <" + node.toString() + "> <http://dbpedia.org/ontology/country> ?o.}";

				QueryExecution qe1 = qef.createQueryExecution(q);
				ResultSet results1 = qe1.execSelect();
				while (results1.hasNext()) {
					RDFNode node1 = results1.next().get("o");
					q = "SELECT ?o where { <" + node1.toString() + "> <http://dbpedia.org/ontology/demonym> ?o.}";

					QueryExecution qe2 = qef.createQueryExecution(q);
					ResultSet results2 = qe2.execSelect();
					if (results2.hasNext()) {
						RDFNode node2 = results2.next().get("o");
						return node2.asLiteral().getValue().toString();
					}

				}
			}
			qe.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return "";
	}

	public boolean getIsAlive(OWLIndividual individual) {
		Set<Triple> result = new HashSet<Triple>();
		try {
			String q;
			q = "SELECT ?o where { " + individual.toString() + " <http://dbpedia.org/ontology/deathDate> ?o.}";
			q += " LIMIT " + maxShownValuesPerProperty + 1;
			QueryExecution qe = qef.createQueryExecution(q);
			ResultSet results = qe.execSelect();
			if (results.hasNext()) {
				return false;
			}
			qe.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	/**
	 * Returns a textual summary of the given entity.
	 *
	 * @return
	 */
	public Map<OWLIndividual, String> getSummaries(Set<OWLIndividual> individuals, OWLClass nc, String namespace,
			double threshold, Cooccurrence cooccurrence, HardeningType hType) {
		Map<OWLIndividual, String> entity2Summaries = new HashMap<>();

		Map<OWLIndividual, List<NLGElement>> verbalize = verbalize(individuals, nc, namespace, threshold, cooccurrence,
				hType);
		for (Entry<OWLIndividual, List<NLGElement>> entry : verbalize.entrySet()) {
			OWLIndividual individual = entry.getKey();
			List<NLGElement> elements = entry.getValue();
			String summary = realize(elements);
			summary = summary.replaceAll("\\s?\\((.*?)\\)", "");
			summary = summary.replace(" , among others,", ", among others,");
			entity2Summaries.put(individual, summary);
		}

		return entity2Summaries;
	}

	/**
	 * Returns the most specific type of a given individual.
	 * 
	 * @param ind
	 * @return
	 */
	private OWLClass getMostSpecificType(OWLIndividual ind) {
		logger.debug("Getting the most specific type of " + ind);
		String query = String.format(
				"select distinct ?type where {" + " <%s> a ?type ." + "?type a owl:Class ."
						+ "filter not exists {?subtype ^a <%s> ; rdfs:subClassOf ?type .filter(?subtype != ?type)}}",
				ind.toStringID(), ind.toStringID());

		SortedSet<OWLClass> types = new TreeSet<OWLClass>();

		QueryExecution qe = qef.createQueryExecution(query);
		ResultSet rs = qe.execSelect();
		while (rs.hasNext()) {
			QuerySolution qs = rs.next();
			if (qs.get("type").isURIResource()) {
				types.add(new OWLClassImpl(IRI.create(qs.getResource("type").getURI())));
			}
		}
		qe.close();

		// of more than one type exists, we have to choose one
		// TODO

		return types.first();
	}

	/**
	 * Returns a list of synonymous expressions as subject for the given
	 * resource.
	 * 
	 * @param resource
	 *            the resource
	 * @param resourceType
	 *            the type of the resource
	 * @param resourceGender
	 *            the gender of the resource
	 * @return list of synonymous expressions
	 */
	public List<NPPhraseSpec> generateSubjects(Resource resource, OWLClass resourceType, Gender resourceGender) {
		List<NPPhraseSpec> result = new ArrayList<NPPhraseSpec>();
		// the textual representation of the resource itself
		result.add(nlg.getNPPhrase(resource.getURI(), false, false));
		// the class, e.g. 'this book'
		NPPhraseSpec np = nlg.getNPPhrase(resourceType.toStringID(), false);
		np.addPreModifier("This");
		result.add(np);
		// the pronoun depending on the gender of the resource
		if (resourceGender.equals(Gender.MALE)) {
			result.add(nlg.nlgFactory.createNounPhrase("he"));
		} else if (resourceGender.equals(Gender.FEMALE)) {
			result.add(nlg.nlgFactory.createNounPhrase("she"));
		} else {
			result.add(nlg.nlgFactory.createNounPhrase("it"));
		}
		return result;
	}

	/**
	 * Returns the gender of the given resource.
	 * 
	 * @param resource
	 * @return the gender
	 */
	public Gender getGender(Resource resource) {
		// get a textual representation of the resource
		String label = realiser.realiseSentence(nlg.getNPPhrase(resource.getURI(), false, false));
		// we take the first token because we assume this is the first name
		String firstToken = label.split(" ")[0];
		// lookup the gender
		Gender g = gender.getGender(resource.getURI(), firstToken);
		return g;
	}

	/**
	 * @param maxShownValuesPerProperty
	 *            the maxShownValuesPerProperty to set
	 */
	public void setMaxShownValuesPerProperty(int maxShownValuesPerProperty) {
		this.maxShownValuesPerProperty = maxShownValuesPerProperty;
	}

	/**
	 * Replaces the subject of a coordinated phrase or simple phrase with a
	 * subject from a list of precomputed subjects
	 *
	 * @param phrase
	 * @param subjects
	 * @return Phrase with replaced subject
	 */
	protected NLGElement replaceSubject(NLGElement phrase, List<NPPhraseSpec> subjects, Gender g) {
		SPhraseSpec sphrase;
		if (phrase instanceof SPhraseSpec) {
			sphrase = (SPhraseSpec) phrase;
		} else if (phrase instanceof CoordinatedPhraseElement) {
			sphrase = (SPhraseSpec) ((CoordinatedPhraseElement) phrase).getChildren().get(0);
		} else {
			return phrase;
		}
		int index = (int) Math.floor(Math.random() * subjects.size());
		// index = 2;

		// possessive as specifier of the NP
		NLGElement currentSubject = sphrase.getSubject();
		if (currentSubject.hasFeature(InternalFeature.SPECIFIER) && currentSubject
				.getFeatureAsElement(InternalFeature.SPECIFIER).getFeatureAsBoolean(Feature.POSSESSIVE)) // possessive
																											// subject
		{

			NPPhraseSpec newSubject = nlg.nlgFactory.createNounPhrase(((NPPhraseSpec) currentSubject).getHead());

			NPPhraseSpec newSpecifier = nlg.nlgFactory.createNounPhrase(subjects.get(index));
			newSpecifier.setFeature(Feature.POSSESSIVE, true);
			newSubject.setSpecifier(newSpecifier);

			if (index >= subjects.size() - 1) {
				if (g.equals(Gender.MALE)) {
					newSpecifier.setFeature(LexicalFeature.GENDER, simplenlg.features.Gender.MASCULINE);
				} else if (g.equals(Gender.FEMALE)) {
					newSpecifier.setFeature(LexicalFeature.GENDER, simplenlg.features.Gender.FEMININE);
				} else {
					newSpecifier.setFeature(LexicalFeature.GENDER, simplenlg.features.Gender.NEUTER);
				}
				newSpecifier.setFeature(Feature.PRONOMINAL, true);
			}
			if (currentSubject.isPlural()) {
				newSubject.setPlural(true);
				newSpecifier.setFeature(Feature.NUMBER, NumberAgreement.SINGULAR);
			}
			sphrase.setSubject(newSubject);
		} else {
			currentSubject.setFeature(Feature.PRONOMINAL, true);
			if (g.equals(Gender.MALE)) {
				currentSubject.setFeature(LexicalFeature.GENDER, simplenlg.features.Gender.MASCULINE);
			} else if (g.equals(Gender.FEMALE)) {
				currentSubject.setFeature(LexicalFeature.GENDER, simplenlg.features.Gender.FEMININE);
			} else {
				currentSubject.setFeature(LexicalFeature.GENDER, simplenlg.features.Gender.NEUTER);
			}
		}
		return phrase;
	}

	public static void main(String args[]) throws IOException {
		OptionParser parser = new OptionParser();
		parser.acceptsAll(Lists.newArrayList("e", "endpoint"), "SPARQL endpoint URL to be used.").withRequiredArg()
				.ofType(URL.class);
		parser.acceptsAll(Lists.newArrayList("g", "graph"), "URI of default graph for queries on SPARQL endpoint.")
				.withOptionalArg().ofType(String.class);
		parser.acceptsAll(Lists.newArrayList("c", "class"), "Class of the entity to summarize.").withRequiredArg()
				.ofType(URI.class);
		parser.acceptsAll(Lists.newArrayList("i", "individual"), "Entity to summarize.").withRequiredArg()
				.ofType(URI.class);
		parser.acceptsAll(Lists.newArrayList("cache"), "Path to cache directory.").withOptionalArg();
		parser.acceptsAll(Lists.newArrayList("wordnet"), "Path to WordNet dictionary.").withOptionalArg();

		// parse options and display a message for the user in case of problems
		OptionSet options = null;
		try {
			options = parser.parse(args);
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage() + ". Use -? to get help.");
			System.exit(0);
		}

		// print help screen
		if (options.has("?")) {
			parser.printHelpOn(System.out);
		} else {

		}
		URL endpointURL = null;
		try {
			endpointURL = (URL) options.valueOf("endpoint");
		} catch (OptionException e) {
			System.out.println("The specified endpoint appears not be a proper URL.");
			System.exit(0);
		}
		String defaultGraphURI = null;
		if (options.has("g")) {
			try {
				defaultGraphURI = (String) options.valueOf("graph");
				URI.create(defaultGraphURI);
			} catch (OptionException e) {
				System.out.println("The specified graph appears not be a proper URI.");
				System.exit(0);
			}
		}
		QueryExecutionFactory qef = new QueryExecutionFactoryHttp(endpointURL.toString(), defaultGraphURI);

		String cacheDirectory = (String) options.valueOf("cache");
		if (cacheDirectory != null) {
			long timeToLive = TimeUnit.DAYS.toMillis(30);
			qef = CacheUtilsH2.createQueryExecutionFactory(qef, cacheDirectory, false, timeToLive);
		}

		String wordnetDirectory = (String) options.valueOf("wordnet");

		Verbalizer v = new Verbalizer(qef, cacheDirectory, wordnetDirectory);

		OWLClass cls = new OWLClassImpl(IRI.create((URI) options.valueOf("c")));
		OWLIndividual ind = new OWLNamedIndividualImpl(IRI.create(((URI) options.valueOf("i")).toString()));

		v.setPersonTypes(Sets.newHashSet("http://dbpedia.org/ontology/Person"));

		int maxShownValuesPerProperty = 3;
		v.setMaxShownValuesPerProperty(maxShownValuesPerProperty);
		List<NLGElement> text = v.verbalize(ind, cls, "http://dbpedia.org/ontology/", 0.4, Cooccurrence.PROPERTIES,
				HardeningType.SMALLEST);

		String summary = v.realize(text);
		summary = summary.replaceAll("\\s?\\((.*?)\\)", "");
		summary = summary.replace(" , among others,", ", among others,");
		System.out.println(summary);
	}
}
