package resttest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import javax.ws.rs.Produces;
import java.awt.*;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.aksw.avatar.Verbalizer;
import org.aksw.avatar.WikidataTest;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.junit.Test;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.repository.RepositoryException;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLIndividual;

import uk.ac.manchester.cs.owl.owlapi.OWLClassImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;



@Path("resource1")
public class Resource1 {

    //set up the SPARQL endpoint, in our case it's DBpedia
    private static final SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();

    //create the verbalizer used to generate the textual summarization
    private static final Verbalizer verbalizer = new Verbalizer(endpoint, "cache", null);
    
    private static final WikidataTest wikidataverbalizer= new WikidataTest();
    /*
    @GET
    public String testing(){
    	return "hello world";
    }
    */
    @Path("/getinfo")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getInfo(@QueryParam("Subject")String subject){

        //OWLClass cls = new OWLClassImpl(IRI.create("http://dbpedia.org/ontology/"+classname));

        //define the entity to summarize
        OWLIndividual ind = new OWLNamedIndividualImpl(IRI.create("http://dbpedia.org/resource/"+subject));

        //compute summarization of the entity and verbalize it
        String summary = verbalizer.summarize(ind);

        return summary;

    }
    
    @Path("/getSimilar")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getSimilar(@QueryParam("Subject")String subject){

        //OWLClass cls = new OWLClassImpl(IRI.create("http://dbpedia.org/ontology/"+classname));

        //define the entity to summarize
        OWLIndividual ind = new OWLNamedIndividualImpl(IRI.create("http://dbpedia.org/resource/"+subject));

        //compute summarization of the entity and verbalize it
        String similar = verbalizer.getSimilarEntities(ind);

        return similar;

    }
    
    @Path("/getThumnail")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getThumnail(@QueryParam("Subject")String subject){

        //OWLClass cls = new OWLClassImpl(IRI.create("http://dbpedia.org/ontology/"+classname));

        //define the entity to summarize
        OWLIndividual ind = new OWLNamedIndividualImpl(IRI.create("http://dbpedia.org/resource/"+subject));

        //compute summarization of the entity and verbalize it
        String thumbnail = verbalizer.getThumbnail(ind);

        return thumbnail;

    }
    
    @Path("/getImageWikidata")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getImageWikidata(@QueryParam("Subject")String subjectidentifier){
    	System.out.println("hi");
    	String subjectinput="";
    	String predicateinput="";
    	if(!(subjectidentifier == null)) {
    		subjectinput=subjectidentifier;
    	}
    	
        //compute summarization of the entity and verbalize it
        String img="";
		try {
			img = WikidataTest.getImage(subjectinput);
		} catch ( QueryEvaluationException | RepositoryException
				| MalformedQueryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        return img;

    }
    
    @Path("/getWikidata")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getWikidata(@QueryParam("Subject")String subjectidentifier,@QueryParam("Predicate")String predicateidentifier){

    	String subjectinput="";
    	String predicateinput="";
    	if(!(subjectidentifier == null)) {
    		subjectinput=subjectidentifier;
    	}
    	if(!(predicateidentifier == null)) {
    		predicateinput=predicateidentifier;
    	}
        //compute summarization of the entity and verbalize it
        String wikidatasummary="";
		try {
			wikidatasummary = WikidataTest.wikidatamain(subjectinput, predicateinput);
		} catch (TupleQueryResultHandlerException | QueryEvaluationException | RepositoryException
				| MalformedQueryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        return wikidatasummary;

    }

    


}
