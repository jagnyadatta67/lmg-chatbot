package com.lmg.online.chatbot.ai.project.controller;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cache Management REST Controller
 */
@RestController
@RequestMapping("/api/cache")
@Slf4j
public class CacheManagementController {

    private final CacheManager cacheManager;

    @Autowired
    public CacheManagementController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Get cache statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, CacheStats>> getCacheStats() {
        Map<String, CacheStats> stats = new HashMap<>();

        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCache) {
                CaffeineCache caffeineCache = (CaffeineCache) cache;
                com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                        caffeineCache.getNativeCache();

                CacheStats cacheStats = nativeCache.stats();
                stats.put(cacheName, cacheStats);
            }
        });

        return ResponseEntity.ok(stats);
    }

    /**
     * Clear all caches
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, String>> clearAllCaches() {
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("Cleared cache: {}", cacheName);
            }
        });

        return ResponseEntity.ok(Map.of(
                "message", "All caches cleared successfully",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Clear specific cache
     */
    @DeleteMapping("/clear/{cacheName}")
    public ResponseEntity<Map<String, String>> clearCache(@PathVariable String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.info("Cleared cache: {}", cacheName);
            return ResponseEntity.ok(Map.of(
                    "message", "Cache cleared: " + cacheName,
                    "timestamp", Instant.now().toString()
            ));
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Get cache entry by key
     */
    @GetMapping("/{cacheName}/{key}")
    public ResponseEntity<?> getCacheEntry(
            @PathVariable String cacheName,
            @PathVariable String key
    ) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                return ResponseEntity.ok(wrapper.get());
            }
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Evict cache entry by key
     */
    @DeleteMapping("/{cacheName}/{key}")
    public ResponseEntity<Map<String, String>> evictCacheEntry(
            @PathVariable String cacheName,
            @PathVariable String key
    ) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            log.info("Evicted key '{}' from cache: {}", key, cacheName);
            return ResponseEntity.ok(Map.of(
                    "message", "Cache entry evicted",
                    "cache", cacheName,
                    "key", key
            ));
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Get cache info
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getCacheInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("cacheNames", cacheManager.getCacheNames());
        info.put("cacheCount", cacheManager.getCacheNames().size());
        info.put("timestamp", Instant.now().toString());

        Map<String, Long> cacheSizes = new HashMap<>();
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCache) {
                CaffeineCache caffeineCache = (CaffeineCache) cache;
                long size = caffeineCache.getNativeCache().estimatedSize();
                cacheSizes.put(cacheName, size);
            }
        });
        info.put("cacheSizes", cacheSizes);

        return ResponseEntity.ok(info);
    }

    /**
     * Warm up cache with common queries
     */
    @PostMapping("/warmup")
    public ResponseEntity<Map<String, String>> warmupCache(
            @RequestBody List<String> commonQueries
    ) {
        log.info("Starting cache warmup with {} queries", commonQueries.size());

        // This would call your chatbot service to pre-populate cache
        // Implementation depends on your service structure

        return ResponseEntity.ok(Map.of(
                "message", "Cache warmup initiated",
                "queryCount", String.valueOf(commonQueries.size())
        ));
    }
}
