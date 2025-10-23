package com.lmg.online.chatbot.ai.project.intent;

import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.project.handler.general.GeneralQueryIntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Main entry point with caching - Routes user query to appropriate handler
 */
@Service
@Slf4j
public class ChatbotService {

    private final IntentClassifier intentClassifier;
    private final Map<String, IntentHandler<?>> intentHandlers;
    private final GeneralQueryIntentHandler generalQueryHandler;
    private final CacheManager cacheManager;

    @Autowired
    public ChatbotService(
            IntentClassifier intentClassifier,
            Map<String, IntentHandler<?>> intentHandlers,
            GeneralQueryIntentHandler generalQueryHandler,
            CacheManager cacheManager
    ) {
        this.intentClassifier = intentClassifier;
        this.intentHandlers = intentHandlers;
        this.generalQueryHandler = generalQueryHandler;
        this.cacheManager = cacheManager;
    }

    /**
     * Main query handler with caching
     */
    public ChatbotResponse<?> handleUserQuery(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        String query = request.getMessage();

        log.info("📨 Processing query: {}", query);

        try {
            // Step 0: Check if response is cached (for cacheable queries)
            ChatbotResponse<?> cachedResponse = getCachedResponse(request);
            if (cachedResponse != null) {
                log.info("✅ Cache hit for query: {}", query);
                return enrichResponseWithCacheInfo(cachedResponse, startTime, true);
            }

            log.info("❌ Cache miss for query: {}", query);

            // Step 1: Try pattern-based routing first (fast path)
            IntentHandler<?> handler = null;

            // Step 2: If no pattern match, use AI classifier (with caching)
            if (handler == null) {
                String intentType = getIntentWithCache(query);
                handler = intentHandlers.get(intentType);

                if (handler == null) {
                    log.warn("⚠️ No handler found for intent: {}, using general handler", intentType);
                    handler = generalQueryHandler;
                }
            }

            // Step 3: Execute the handler
            log.info("🎯 Routing to handler: {}", handler.getIntentType());
            ChatbotResponse<?> response = handler.handle(request, startTime);

            // Step 4: Cache the response if eligible
            cacheResponseIfEligible(request, response);

            return enrichResponseWithCacheInfo(response, startTime, false);

        } catch (Exception e) {
            log.error("❌ Error processing query: {}", query, e);
            return handleError(request, startTime, e);
        }
    }

    /**
     * Get intent classification with caching
     */
    @Cacheable(value = "intentClassifications", key = "#query", unless = "#result == null")
    private String getIntentWithCache(String query) {
        log.info("🔍 Classifying intent for: {}", query);
        return intentClassifier.classify(query);
    }

    /**
     * Get cached response if available
     */
    private ChatbotResponse<?> getCachedResponse(ChatRequest request) {
        if (!isCacheable(request)) {
            return null;
        }

        Cache cache = cacheManager.getCache("chatbotResponses");
        if (cache != null) {
            String cacheKey = generateCacheKey(request);
            Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null) {
                return (ChatbotResponse<?>) wrapper.get();
            }
        }
        return null;
    }

    /**
     * Cache response if eligible
     */
    private void cacheResponseIfEligible(ChatRequest request, ChatbotResponse<?> response) {
        if (!isCacheable(request) || response == null) {
            return;
        }

        Cache cache = cacheManager.getCache("chatbotResponses");
        if (cache != null) {
            String cacheKey = generateCacheKey(request);
            cache.put(cacheKey, response);
            log.info("💾 Cached response for key: {}", cacheKey);
        }
    }

    /**
     * Determine if request is cacheable
     */
    private boolean isCacheable(ChatRequest request) {
        // Don't cache queries with sensitive information
        if (request.getCardNumber() != null || request.getPin() != null) {
            return false;
        }

        // Don't cache if previous response is provided (conversational context)
        if (request.getPreviousResponse() != null) {
            return false;
        }

        // Don't cache time-sensitive queries
        String message = request.getMessage().toLowerCase();
        if (message.contains("now") || message.contains("current") ||
                message.contains("today") || message.contains("latest")) {
            return false;
        }

        return true;
    }

    /**
     * Generate cache key for request
     */
    private String generateCacheKey(ChatRequest request) {
        StringBuilder keyBuilder = new StringBuilder();

        if (request.getUserId() != null) {
            keyBuilder.append(request.getUserId()).append(":");
        }

        String normalizedMessage = request.getMessage()
                .toLowerCase()
                .trim()
                .replaceAll("\\s+", " ");
        keyBuilder.append(normalizedMessage);

        if (request.getLatitude() != 0 && request.getLongitude() != 0) {
            keyBuilder.append(":loc:")
                    .append(String.format("%.2f,%.2f", request.getLatitude(), request.getLongitude()));
        }

        if (request.getEnv() != null) {
            keyBuilder.append(":env:").append(request.getEnv());
        }

        String cachekKey= DigestUtils.md5DigestAsHex(keyBuilder.toString().getBytes());
        log.info(" key value  {}  gerenated key",keyBuilder,cachekKey);
        return  cachekKey;
    }

    /**
     * Add cache metadata to response
     */
    private ChatbotResponse<?> enrichResponseWithCacheInfo(
            ChatbotResponse<?> response,
            long startTime,
            boolean fromCache
    ) {
        long processingTime = System.currentTimeMillis() - startTime;

        // Add metadata to response (assuming ChatbotResponse has metadata field)
        if (response.getMetadata() == null) {
            response.setMetadata(new HashMap<>());
        }
        response.getMetadata().put("cached", fromCache);
        response.getMetadata().put("processingTimeMs", processingTime);

        log.info("⏱️ Total processing time: {}ms (cached: {})", processingTime, fromCache);

        return response;
    }

    /**
     * Error handler
     */
    private ChatbotResponse<?> handleError(ChatRequest request, long startTime, Exception e) {

        ChatbotResponse<?> errorResponse = ChatbotResponse.builder().
                success(false).errorResponse("An error occurred while processing your request.").metadata(
                        Map.of(
                "error", e.getMessage(),
                "processingTimeMs", System.currentTimeMillis() - startTime
        )).build()
;
        return errorResponse;
    }

    /**
     * Manually invalidate cache for user
     */
    public void invalidateUserCache(String userId) {
        Cache cache = cacheManager.getCache("chatbotResponses");
        if (cache != null) {
            cache.clear();
            log.info("🗑️ Cleared cache for user: {}", userId);
        }
    }
}