package datawave.microservice.cached;

import com.hazelcast.spring.cache.HazelcastCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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
            return new UnsupportedOperationCacheInspector();
    }
    
    private static class UnsupportedOperationCacheInspector implements CacheInspector {
        @Override
        public <T> T list(String cacheName, Class<T> cacheObjectType, String key) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public <T> List<? extends T> listAll(String cacheName, Class<T> cacheObjectType) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public <T> List<? extends T> listMatching(String cacheName, Class<T> cacheObjectType, String substring) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public <T> int evictMatching(String cacheName, Class<T> cacheObjectType, String substring) {
            throw new UnsupportedOperationException();
        }
    }
}
