package com.lmg.online.chatbot.ai.project.handler.chatbotorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.chatbotorder.ChatbotOrderTool;
import com.lmg.online.chatbot.ai.tools.chatbotorder.dto.ChatbotOrderListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Handles ORDER_LISTING intent — shows customer's recent order list
 * via the chatbot-specific order endpoint.
 *
 * Triggered by: "show my orders", "my recent orders", "order history",
 *               "what did I buy", "list my orders"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatbotOrderIntentHandler implements IntentHandler<ChatbotOrderListResponse> {

    private static final Pattern ORDER_LIST_PATTERN = Pattern.compile(
            // Only match PLURAL "orders" or explicit listing phrases — never match singular "order"
            // so that "get my order", "my order status" etc. fall through to ORDER_TRACKING
            ".*\\b(orders|my\\s*orders|show\\s*(my\\s*)?orders|view\\s*(my\\s*)?orders|" +
            "order\\s*histor(y|ies)|order\\s*list|recent\\s*orders|past\\s*orders|" +
            "all\\s*(my\\s*)?orders|list.*orders|what.*bought|what.*ordered|" +
            "purchase.*histor(y|ies)|my\\s*purchases)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private final ChatbotOrderTool chatbotOrderTool;
    private final AiAnalyticsService aiAnalyticsService;
    private final ObjectMapper objectMapper;

    @Override
    public ChatbotResponse<ChatbotOrderListResponse> handle(ChatRequest request, long startTime) {
        log.info("📦 Handling ORDER_LISTING intent");

        if (!isUserAuthenticated(request)) {
            return unauthenticatedResponse(startTime);
        }

        ChatbotOrderListResponse data = chatbotOrderTool.getChatbotOrderList(
                request.getUserId(),
                request.getConcept(),
                request.getEnv(),
                request.getAppid(),request.getAccessToken(),
                null   // default count = 10
        );

        return buildResponse(data, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "ORDER_LISTING";
    }

    @Override
    public boolean canHandle(String query) {
        return ORDER_LIST_PATTERN.matcher(query).matches();
    }

    private boolean isUserAuthenticated(ChatRequest request) {
        return request.getUserId() != null && !request.getUserId().isBlank();
    }

    private ChatbotResponse<ChatbotOrderListResponse> unauthenticatedResponse(long startTime) {
        ChatbotOrderListResponse r = new ChatbotOrderListResponse();
        r.setChatMessage("Please sign in to continue — once you're logged in, I can fetch your orders.");
        return buildResponse(r, null, startTime);
    }

    private ChatbotResponse<ChatbotOrderListResponse> buildResponse(
            ChatbotOrderListResponse data, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;

        String rawMessage = request != null ? request.getMessage() : "";
        String responseText = "";
        try {
            responseText = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            responseText = data.getChatMessage() != null ? data.getChatMessage() : "";
        }

        aiAnalyticsService.trackUsage(
                null, null,
                rawMessage,
                responseText,
                0, 0,
                "gpt-4o-mini",
                "stop",
                true,
                "getChatbotOrderList",
                responseTime
        );

        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);

        return ChatbotResponse.<ChatbotOrderListResponse>builder()
                .data(data)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }
}
