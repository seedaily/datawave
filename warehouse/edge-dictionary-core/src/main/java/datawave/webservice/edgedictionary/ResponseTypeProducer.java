package datawave.webservice.edgedictionary;

import datawave.query.tables.edge.EdgeDictionaryType;
import datawave.webservice.results.edgedictionary.DefaultEdgeDictionary;
import org.springframework.core.ParameterizedTypeReference;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

@ApplicationScoped
public class ResponseTypeProducer {
    @Produces
    @EdgeDictionaryType
    public ParameterizedTypeReference<DefaultEdgeDictionary> produceEdgeDictionaryType() {
        return new ParameterizedTypeReference<DefaultEdgeDictionary>() {};
    }
}
