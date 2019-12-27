package org.aksw.avatar;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.awt.*;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLIndividual;

import uk.ac.manchester.cs.owl.owlapi.OWLClassImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;



@Path("verbalizer")
public class RESTWebService {

    //set up the SPARQL endpoint, in our case it's DBpedia
    private static final SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();

    //create the verbalizer used to generate the textual summarization
    private static final Verbalizer verbalizer = new Verbalizer(endpoint, "cache", null);

    @Path("/getinfo")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getInfo(@QueryParam("Class")String classname,@QueryParam("Subject")String subject){

        OWLClass cls = new OWLClassImpl(IRI.create("http://dbpedia.org/ontology/"+classname));

        //define the entity to summarize
        OWLIndividual ind = new OWLNamedIndividualImpl(IRI.create("http://dbpedia.org/resource/"+subject));

        //compute summarization of the entity and verbalize it
        String summary = verbalizer.summarize(ind, cls);

        return summary;

    }


}
