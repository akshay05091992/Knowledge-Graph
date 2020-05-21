package resttest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import javax.ws.rs.Produces;
import java.awt.*;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.aksw.avatar.Verbalizer;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.junit.Test;

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
        String summary = verbalizer.getSimilarEntities(ind);

        return summary;

    }

    


}
