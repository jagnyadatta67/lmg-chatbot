package com.lmg.online.chatbot.ai.project.handler;



import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.request.ChatRequest;

/**
 * Base interface for all intent handlers
 */
public interface IntentHandler<T> {

    /**
     * Handles the user request for this specific intent
     * @param request The chat request from the user
     * @param startTime The timestamp when request processing started
     * @return ChatbotResponse with the processed data
     */
    ChatbotResponse<T> handle(ChatRequest request, long startTime);

    /**
     * Returns the intent type this handler manages
     * @return Intent type as string
     */
    String getIntentType();

    /**
     * Checks if this handler can handle the given query
     * @param query The user's query
     * @return true if this handler can process the query
     */
    default boolean canHandle(String query) {
        return false;
    }
}