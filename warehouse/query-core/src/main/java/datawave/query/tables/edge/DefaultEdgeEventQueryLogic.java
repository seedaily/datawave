package datawave.query.tables.edge;

import datawave.query.QueryParameters;
import datawave.query.model.edge.EdgeQueryModel;
import datawave.query.jexl.JexlASTHelper;
import datawave.query.jexl.visitors.JexlStringBuildingVisitor;
import datawave.query.jexl.visitors.QueryModelVisitor;
import datawave.query.tables.ShardQueryLogic;
import datawave.security.authorization.DatawavePrincipal;
import datawave.security.authorization.JWTTokenHandler;
import datawave.security.system.CallerPrincipal;
import datawave.security.system.InternalJWTCall;
import datawave.webservice.query.Query;
import datawave.webservice.query.configuration.GenericQueryConfiguration;
import datawave.webservice.results.edgedictionary.EdgeDictionaryBase;
import datawave.webservice.results.edgedictionary.MetadataBase;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.commons.jexl2.parser.ASTJexlScript;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.apache.log4j.Logger;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;

/**
 * This Logic highjacks the Query string, and transforms it into a ShardQueryLogic query The query string is of the form:
 * 
 * SOURCE == xxx AND SINK == yyy AND TYPE == zzz AND RELATIONSHIP == www AND EDGE_ATTRIBUTE1 == vvv
 * 
 */
public class DefaultEdgeEventQueryLogic extends ShardQueryLogic {
    
    private static final Logger log = Logger.getLogger(DefaultEdgeEventQueryLogic.class);
    
    private String edgeModelName = null;
    private EdgeQueryModel edgeQueryModel = null;
    
    protected EdgeDictionaryBase<?,? extends MetadataBase<?>> dict;
    
    @Inject
    @CallerPrincipal
    protected DatawavePrincipal callerPrincipal;
    
    @Inject
    @InternalJWTCall
    protected JWTTokenHandler jwtTokenHandler;
    
    @Inject
    @ConfigProperty(name = "dw.edgedictionary.url", defaultValue = "http://dictionary:8080/dictionary/edge/v1/")
    protected String edgeDictionaryURL;
    
    @Inject
    @EdgeDictionaryType
    protected ParameterizedTypeReference<? extends EdgeDictionaryBase<?,? extends MetadataBase<?>>> edgeDictionaryType;
    
    protected WebClient webClient;
    
    public DefaultEdgeEventQueryLogic() {}
    
    // Required for clone method. Clone is called by the Logic Factory. If you don't override
    // the method, the Factory will create an instance of the super class instead of
    // this logic.
    public DefaultEdgeEventQueryLogic(DefaultEdgeEventQueryLogic other) {
        super(other);
        this.dict = other.dict;
        this.edgeModelName = other.edgeModelName;
        this.edgeQueryModel = other.edgeQueryModel;
    }
    
    @PostConstruct
    public void postConstruct() {
        webClient = WebClient.builder().baseUrl(edgeDictionaryURL).build();
    }
    
    @Override
    public DefaultEdgeEventQueryLogic clone() {
        return new DefaultEdgeEventQueryLogic(this);
    }
    
    @SuppressWarnings("unchecked")
    protected EdgeDictionaryBase<?,? extends MetadataBase<?>> getEdgeDictionary(Connector connector, Set<Authorizations> auths, int numThreads)
                    throws Exception {
        
        return webClient.get()
                        .header("Authorization", "Bearer " + jwtTokenHandler.createTokenFromUsers(callerPrincipal.getName(), callerPrincipal.getProxiedUsers()))
                        .retrieve().bodyToMono(edgeDictionaryType).block();
    }
    
    protected DefaultEventQueryBuilder getEventQueryBuilder() {
        return new DefaultEventQueryBuilder(dict);
    }
    
    @Override
    public GenericQueryConfiguration initialize(Connector connection, Query settings, Set<Authorizations> auths) throws Exception {
        
        setEdgeDictionary(getEdgeDictionary(connection, auths, 8)); // TODO grab threads from somewhere
        
        // Load and apply the configured edge query model
        loadEdgeQueryModel(connection, auths);
        
        String queryString = applyQueryModel(getJexlQueryString(settings));
        
        DefaultEventQueryBuilder eventQueryBuilder = getEventQueryBuilder();
        
        // punch in the new query String
        settings.setQuery(eventQueryBuilder.getEventQuery(queryString));
        
        // new query string will always be in the JEXL syntax
        settings.addParameter(QueryParameters.QUERY_SYNTAX, "JEXL");
        
        return super.initialize(connection, settings, auths);
    }
    
    /**
     * Loads the query model specified by the current configuration, to be applied to the incoming query.
     */
    protected void loadEdgeQueryModel(Connector connector, Set<Authorizations> auths) {
        String model = getEdgeModelName() == null ? "" : getEdgeModelName();
        String modelTable = getModelTableName() == null ? "" : getModelTableName();
        if (null == getEdgeQueryModel() && (!model.isEmpty() && !modelTable.isEmpty())) {
            try {
                setEdgeQueryModel(new EdgeQueryModel(getMetadataHelperFactory().createMetadataHelper(connector, config.getMetadataTableName(), auths)
                                .getQueryModel(config.getModelTableName(), config.getModelName())));
            } catch (Throwable t) {
                log.error("Unable to load edgeQueryModel from metadata table", t);
            }
        }
    }
    
    /**
     * Parses the Jexl Query string into an ASTJexlScript and then uses QueryModelVisitor to apply the loaded model to the query string, and then rewrites the
     * translated ASTJexlScript back to a query string using JexlStringBuildingVisitor.
     * 
     * @param queryString
     * @return modified query string
     */
    protected String applyQueryModel(String queryString) {
        ASTJexlScript origScript = null;
        ASTJexlScript script = null;
        try {
            origScript = JexlASTHelper.parseJexlQuery(queryString);
            HashSet<String> allFields = new HashSet<>();
            allFields.addAll(getEdgeQueryModel().getAllInternalFieldNames());
            script = QueryModelVisitor.applyModel(origScript, getEdgeQueryModel(), allFields);
            return JexlStringBuildingVisitor.buildQuery(script);
            
        } catch (Throwable t) {
            throw new IllegalStateException("Edge query model could not be applied", t);
        }
    }
    
    public void setEdgeDictionary(EdgeDictionaryBase<?,? extends MetadataBase<?>> dict) {
        this.dict = dict;
    }
    
    public EdgeQueryModel getEdgeQueryModel() {
        return this.edgeQueryModel;
    }
    
    public void setEdgeQueryModel(EdgeQueryModel model) {
        this.edgeQueryModel = model;
    }
    
    public String getEdgeModelName() {
        return this.edgeModelName;
    }
    
    public void setEdgeModelName(String edgeModelName) {
        this.edgeModelName = edgeModelName;
    }
    
    protected String getEventQuery(Query settings) throws Exception {
        return getEventQueryBuilder().getEventQuery(getJexlQueryString(settings));
    }
    
}
