package datawave.microservice.cached;

import com.hazelcast.spring.cache.HazelcastCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides an instance of a {@link CacheInspector}.
 */
@Configuration
@ConditionalOnClass(CacheManager.class)
@ConditionalOnMissingBean(CacheInspector.class)
public class CacheInspectorConfiguration {
    
    @Bean
    public CacheInspector cacheInspector(CacheManager cacheManager) {
        if (cacheManager instanceof HazelcastCacheManager)
            return new HazelcastCacheInspector(cacheManager);
        else if (cacheManager instanceof CaffeineCacheManager)
            return new CaffeineCacheInspector(cacheManager);
        else
            throw new IllegalArgumentException("CacheManager of type " + cacheManager.getClass() + " is unsupported.");
    }
}
