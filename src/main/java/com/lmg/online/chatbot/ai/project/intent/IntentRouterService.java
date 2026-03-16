package com.lmg.online.chatbot.ai.project.intent;

import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;

import com.lmg.online.chatbot.ai.project.handler.general.GeneralQueryIntentHandler;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Main orchestrator service that routes requests to appropriate intent handlers
 */
@Slf4j
@Service
public class IntentRouterService {

    private final Map<String, IntentHandler<?>> intentHandlers;
    private final IntentClassifier intentClassifier;
    private final IntentHandler<?> generalQueryHandler;

    public IntentRouterService(
            List<IntentHandler<?>> handlers,
            IntentClassifier intentClassifier,
            GeneralQueryIntentHandler generalQueryHandler) {

        // Create a map of intent type -> handler for quick lookup
        this.intentHandlers = handlers.stream()
                .collect(Collectors.toMap(
                        IntentHandler::getIntentType,
                        Function.identity()
                ));

        this.intentClassifier = intentClassifier;
        this.generalQueryHandler = generalQueryHandler;

        log.info("✅ IntentRouterService initialized with {} handlers: {}",
                intentHandlers.size(), intentHandlers.keySet());
    }

    /**
     * Main entry point - Routes user query to appropriate handler
     */
    public ChatbotResponse<?> handleUserQuery(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        String query = request.getMessage();

        log.info("📨 Processing query: {}", query);

        try {
            // Step 0: intentHint bypass — frontend already knows the intent
            // (e.g. user entered an order number after ORDER_TRACKING prompted for it).
            // Skip pattern matching and AI classification entirely.
            IntentHandler<?> handler = resolveByIntentHint(request);

            if (handler == null) {
                // Step 1: Try pattern-based routing first (fast path — no AI token cost)
                handler = findHandlerByPattern(query);
            }

            // Step 2: If no pattern match, use AI classifier
            if (handler == null) {
                String intentType = intentClassifier.classify(query);
                handler = intentHandlers.get(intentType);

                if (handler == null) {
                    log.warn("⚠️ No handler found for intent: {}, using general handler", intentType);
                    handler = generalQueryHandler;
                }
            }

            // Step 3: Execute the handler
            log.info("🎯 Routing to handler: {}", handler.getIntentType());
            return handler.handle(request, startTime);

        } catch (Exception e) {
            log.error("❌ Error processing query: {}", query, e);
            return handleError(request, startTime, e);
        }
    }

    /**
     * Step 0 bypass: when the frontend explicitly pins an intent via {@code intentHint},
     * skip all classification and return the matching handler directly.
     * Returns {@code null} if no hint is set or the hint doesn't match a registered handler.
     */
    private IntentHandler<?> resolveByIntentHint(ChatRequest request) {
        String hint = request.getIntentHint();
        if (hint == null || hint.isBlank()) return null;
        String normalized = hint.trim().toUpperCase();
        IntentHandler<?> handler = intentHandlers.get(normalized);
        if (handler != null) {
            log.info("📌 intentHint bypass → {}", normalized);
        } else {
            log.warn("⚠️ intentHint '{}' not recognised — falling through to normal routing", normalized);
        }
        return handler;
    }

    /**
     * Fast path: Check if any handler can handle based on pattern matching
     */
    private IntentHandler<?> findHandlerByPattern(String query) {
        return intentHandlers.values().stream()
                .filter(handler -> handler.canHandle(query))
                .findFirst()
                .orElse(null);
    }

    /**
     * Error handling with fallback response
     */
    private ChatbotResponse<?> handleError(ChatRequest request, long startTime, Exception e) {
        long responseTime = System.currentTimeMillis() - startTime;

        String errorMessage = "I apologize, but I encountered an error processing your request. " +
                "Please try again or contact support at 1800-123-1555.";

        return ChatbotResponse.<String>builder()
                .data(errorMessage)
                .tokenUsage(null)
                .responseTimeMs(responseTime)
                .intent("ERROR")
                .build();
    }

    /**
     * Get all registered intent types (useful for debugging/monitoring)
     */
    public List<String> getRegisteredIntents() {
        return List.copyOf(intentHandlers.keySet());
    }
}