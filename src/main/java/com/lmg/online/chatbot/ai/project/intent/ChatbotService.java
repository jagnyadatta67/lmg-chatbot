package com.lmg.online.chatbot.ai.project.intent;

import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.project.handler.general.GeneralQueryIntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Main entry point — routes every user query directly to the appropriate handler.
 * Caching has been removed so every request always gets a fresh, accurate response.
 */
@Service
@Slf4j
public class ChatbotService {

    private final IntentClassifier intentClassifier;
    private final Map<String, IntentHandler<?>> intentHandlers;
    private final GeneralQueryIntentHandler generalQueryHandler;

    @Autowired
    public ChatbotService(
            IntentClassifier intentClassifier,
            List<IntentHandler<?>> handlers,
            GeneralQueryIntentHandler generalQueryHandler
    ) {
        this.intentClassifier   = intentClassifier;
        this.intentHandlers     = handlers.stream()
                .collect(Collectors.toMap(
                        IntentHandler::getIntentType,
                        Function.identity()
                ));
        this.generalQueryHandler = generalQueryHandler;
    }

    /**
     * Main query handler — no caching, always fresh.
     */
    public ChatbotResponse<?> handleUserQuery(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        String query   = request.getMessage();

        log.info("📨 Processing query: {}", query);

        try {
            // Step 1: Try pattern-based routing (fast path — no AI token cost)
            IntentHandler<?> handler = findHandlerByPattern(query);

            // Step 2: If no pattern match, use AI classifier
            if (handler == null) {
                String intentType = intentClassifier.classify(query);
                handler = intentHandlers.get(intentType);

                if (handler == null) {
                    log.warn("⚠️ No handler for intent: {} — using general handler", intentType);
                    handler = generalQueryHandler;
                }
            }

            // Step 3: Execute handler
            log.info("🎯 Routing to handler: {}", handler.getIntentType());
            ChatbotResponse<?> response = handler.handle(request, startTime);

            return enrichResponse(response, startTime);

        } catch (Exception e) {
            log.error("❌ Error processing query: {}", query, e);
            return handleError(startTime, e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fast-path: check if any handler's canHandle() regex matches the query.
     * Avoids an AI classifier call for obvious intents (saves tokens + latency).
     */
    private IntentHandler<?> findHandlerByPattern(String query) {
        return intentHandlers.values().stream()
                .filter(h -> h.canHandle(query))
                .findFirst()
                .orElse(null);
    }

    /**
     * Stamps the response with processing-time metadata and marks it successful.
     */
    private ChatbotResponse<?> enrichResponse(ChatbotResponse<?> response, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;

        if (response.getMetadata() == null) {
            response.setMetadata(new HashMap<>());
        }
        response.getMetadata().put("processingTimeMs", processingTime);
        response.setSuccess(true);

        log.info("⏱️ Total processing time: {}ms", processingTime);
        return response;
    }

    private ChatbotResponse<?> handleError(long startTime, Exception e) {
        return ChatbotResponse.builder()
                .success(false)
                .errorResponse("An error occurred while processing your request.")
                .metadata(Map.of(
                        "error",           e.getMessage(),
                        "processingTimeMs", System.currentTimeMillis() - startTime
                ))
                .build();
    }
}
