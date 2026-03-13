package com.lmg.online.chatbot.ai.project.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache configuration for chatbot responses.
 *
 * TTL and max-size are driven by AppProperties (app.business.cache.*)
 * so they can be tuned per environment without recompiling.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private final AppProperties appProperties;

    public CacheConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "chatbotResponses",
                "intentClassifications",
                "userContext"
        );
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        int ttlHours = appProperties.getBusiness().getCache().getTtlHours();
        int maxSize  = appProperties.getBusiness().getCache().getMaxSize();

        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofHours(ttlHours))
                .recordStats();
    }

    @Bean
    public KeyGenerator customKeyGenerator() {
        return new ChatCacheKeyGenerator();
    }
}
