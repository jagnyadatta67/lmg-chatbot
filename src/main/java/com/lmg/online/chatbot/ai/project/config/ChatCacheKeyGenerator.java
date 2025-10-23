package com.lmg.online.chatbot.ai.project.config;

import com.lmg.online.chatbot.ai.request.ChatRequest;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.util.DigestUtils;


import java.lang.reflect.Method;

/**
 * Custom Key Generator for Chat Queries
 */
public class ChatCacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params.length > 0 && params[0] instanceof ChatRequest) {
            ChatRequest request = (ChatRequest) params[0];
            return generateCacheKey(request);
        }
        return SimpleKeyGenerator.generateKey(params);
    }

    private String generateCacheKey(ChatRequest request) {
        StringBuilder keyBuilder = new StringBuilder();

        // Include userId for personalized responses
        if (request.getUserId() != null) {
            keyBuilder.append(request.getUserId()).append(":");
        }

        // Normalize and include the message
        String normalizedMessage = normalizeQuery(request.getMessage());
        keyBuilder.append(normalizedMessage);

        // Include location-based queries
        if (request.getLatitude() != 0 && request.getLongitude() != 0) {
            keyBuilder.append(":loc:")
                    .append(String.format("%.2f,%.2f", request.getLatitude(), request.getLongitude()));
        }

        // Include environment context
        if (request.getEnv() != null) {
            keyBuilder.append(":env:").append(request.getEnv());
        }

        return DigestUtils.md5DigestAsHex(keyBuilder.toString().getBytes());
    }

    private String normalizeQuery(String query) {
        if (query == null) return "";
        return query.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-z0-9 ]", "");
    }
}