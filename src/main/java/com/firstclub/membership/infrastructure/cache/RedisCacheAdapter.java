package com.firstclub.membership.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Programmatic Redis cache adapter for operations where declarative @Cacheable is not suitable.
 * Primarily used for cache invalidation patterns beyond what @CacheEvict supports.
 */
@Component
public class RedisCacheAdapter {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheAdapter.class);

    private final CacheManager cacheManager;

    public RedisCacheAdapter(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evict(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            log.debug("Cache evicted: cache={} key={}", cacheName, key);
        }
    }

    public void evictAll(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.debug("Cache cleared: cache={}", cacheName);
        }
    }

    public <T> T get(String cacheName, String key, Class<T> type) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                return type.cast(wrapper.get());
            }
        }
        return null;
    }
}
