package datawave.security.system;

import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import datawave.configuration.DatawaveEmbeddedProjectStageHolder;
import datawave.security.authorization.DatawavePrincipal;
import datawave.security.authorization.DatawaveUserService;
import datawave.security.authorization.JWTTokenHandler;
import datawave.security.authorization.SubjectIssuerDNPair;
import datawave.security.user.UserOperationsBean;
import org.apache.deltaspike.core.api.exclude.Exclude;
import org.jboss.security.AuthenticationManager;
import org.jboss.security.CacheableManager;
import org.jboss.security.JSSESecurityDomain;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;

/**
 * A producer class for generating server-security related artifacts. For one, we produce the server DN of the server that we are running inside of. We allso
 * produce the {@link JSSESecurityDomain} for our application. We use this rather than directly injecting at each site using {@link Resource} since the producer
 * allows us to use a plain {@link javax.inject.Inject} annotation versus having to specify the resource name each time we inject with {@link Resource}. This
 * way, we only name the resource once.
 */
@ApplicationScoped
@Exclude(ifProjectStage = DatawaveEmbeddedProjectStageHolder.DatawaveEmbedded.class)
public class ServerSecurityProducer {
    // Allow injection of JSSESecurityDomain without having to specify the JNDI name at each injection point.
    // Instead, users can simply do:
    // @Inject private JSSESecurityDomain jsseSecurityDomain
    // and the specification of the resource location is limited to this class.
    @Produces
    @Resource(name = "java:jboss/jaas/datawave/jsse")
    private JSSESecurityDomain domain;
    
    @Resource(name = "java:jboss/jaas/datawave")
    private AuthenticationManager authenticationManager;
    
    @Inject
    private DatawaveUserService datawaveUserService;
    
    @Inject
    private UserOperationsBean userOperationsBean;
    
    /**
     * Produces a {@link DatawavePrincipal} that is {@link RequestScoped}. This is the principal of the calling user--that is, the principal that is available
     * from the {@link javax.ejb.EJBContext} of an EJB.
     */
    @Produces
    @CallerPrincipal
    @RequestScoped
    public DatawavePrincipal produceCallerPrincipal() throws Exception {
        DatawavePrincipal dp = userOperationsBean.getCurrentPrincipal();
        return dp == null ? DatawavePrincipal.anonymousPrincipal() : dp;
    }
    
    /**
     * Produces a {@link DatawavePrincipal} that is {@link RequestScoped}. This is a principal that is filled in with the name and authorizations for the server
     * that is currently running DATAWAVE.
     */
    @Produces
    @ServerPrincipal
    @RequestScoped
    public DatawavePrincipal produceServerPrincipal() throws Exception {
        return new DatawavePrincipal(datawaveUserService.lookup(Collections.singleton(lookupServerDN())));
    }
    
    @Produces
    @AuthorizationCache
    @SuppressWarnings("unchecked")
    public CacheableManager<Object,Principal> produceAuthManager() {
        return (authenticationManager instanceof CacheableManager) ? (CacheableManager<Object,Principal>) authenticationManager : null;
    }
    
    @Produces
    @InternalJWTCall
    public JWTTokenHandler produceJWTTokenHandler(JSSESecurityDomain jsseSecurityDomain) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new GuavaModule());
        
        try {
            SSLContext ctx = SSLContext.getInstance("TLSv1.2");
            ctx.init(jsseSecurityDomain.getKeyManagers(), jsseSecurityDomain.getTrustManagers(), null);
            
            String alias = jsseSecurityDomain.getKeyStore().aliases().nextElement();
            X509KeyManager keyManager = (X509KeyManager) jsseSecurityDomain.getKeyManagers()[0];
            X509Certificate[] certs = keyManager.getCertificateChain(alias);
            Key signingKey = keyManager.getPrivateKey(alias);
            
            return new JWTTokenHandler(certs[0], signingKey, 24, TimeUnit.HOURS, JWTTokenHandler.TtlMode.RELATIVE_TO_CURRENT_TIME, objectMapper);
        } catch (KeyStoreException | KeyManagementException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
    
    private SubjectIssuerDNPair lookupServerDN() throws KeyStoreException {
        if (domain == null) {
            throw new IllegalArgumentException("Unable to find security domain.");
        }
        
        KeyStore keystore = domain.getKeyStore();
        final X509Certificate cert = (X509Certificate) keystore.getCertificate(keystore.aliases().nextElement());
        final String serverDN = cert.getSubjectX500Principal().getName();
        final String serverIssuerDN = cert.getIssuerX500Principal().getName();
        return SubjectIssuerDNPair.of(serverDN, serverIssuerDN);
    }
}
