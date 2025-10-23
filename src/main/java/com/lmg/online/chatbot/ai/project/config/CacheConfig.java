package com.lmg.online.chatbot.ai.project.config;

import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.DigestUtils;

import java.time.Duration;

/**
 * Cache Configuration for Chatbot Responses
 */
@Configuration
@EnableCaching
public class CacheConfig {

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

    private com.github.benmanes.caffeine.cache.Caffeine<Object, Object> caffeineCacheBuilder() {
        return com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofHours(1))
                .recordStats();
    }

    @Bean
    public KeyGenerator customKeyGenerator() {
        return new ChatCacheKeyGenerator();
    }
}