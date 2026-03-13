package com.lmg.online.chatbot.ai.project.handler.delivery;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.analytics.TokenCostCalculator;
import com.lmg.online.chatbot.ai.analytics.TokenUsage;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.delivery.DeliveryTrackingTool;
import com.lmg.online.chatbot.ai.tools.delivery.dto.DeliveryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Handles DELIVERY_TRACKING intent.
 *
 * Uses AI with the DeliveryTrackingTool so the LLM can extract
 * orderNo + entryPk from the user's natural language message.
 *
 * Triggered by: "where is my delivery", "track my shipment",
 *               "when will order 3400746000 arrive", "delivery status"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryTrackingIntentHandler implements IntentHandler<DeliveryResponse> {

    private static final Pattern DELIVERY_PATTERN = Pattern.compile(
            ".*\\b(delivery|shipment|shipped|dispatch|when.*arrive|track.*order|" +
            "where.*order|out.*delivery|in.*transit)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final String DELIVERY_SYSTEM_PROMPT = """
            You are a delivery tracking assistant for %s (Landmark Group India).
            The customer is asking about delivery status of their order.
            Call getDeliveryDetails() with the orderNo, entryPk, and productCode (if mentioned) extracted from the message.
            The Access Token from the user message context must be passed as-is to the tool — never modify it.
            If orderNo or entryPk are missing from the message, ask the customer to provide them.
            Return a clear, friendly status update based on the tool result.
            """;

    @Qualifier("deliveryClient")
    private final ChatClient deliveryClient;

    private final DeliveryTrackingTool deliveryTrackingTool;
    private final TokenCostCalculator tokenCostCalculator;
    private final AiAnalyticsService aiAnalyticsService;
    private final BeanOutputConverter<DeliveryResponse> deliveryOutputConverter;

    @Override
    public ChatbotResponse<DeliveryResponse> handle(ChatRequest request, long startTime) {
        log.info("🚚 Handling DELIVERY_TRACKING intent");

        if (!isUserAuthenticated(request)) {
            return unauthenticatedResponse(startTime);
        }

        String systemPrompt = String.format(DELIVERY_SYSTEM_PROMPT, request.getConcept());

        String userMessage = String.format(
                "Customer ID: %s | Access Token: %s | Concept: %s | Env: %s | AppId: %s\nQuery: %s",
                request.getUserId(),
                request.getAccessToken(),
                request.getConcept(),
                request.getEnv(),
                request.getAppid(),
                request.getMessage()
        );

        ChatResponse response = deliveryClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .tools(deliveryTrackingTool)
                .call()
                .chatResponse();

        return buildResponse(response, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "DELIVERY_TRACKING";
    }

    @Override
    public boolean canHandle(String query) {
        return DELIVERY_PATTERN.matcher(query).matches();
    }

    private boolean isUserAuthenticated(ChatRequest request) {
        return request.getUserId() != null && !request.getUserId().isBlank();
    }

    private ChatbotResponse<DeliveryResponse> unauthenticatedResponse(long startTime) {
        DeliveryResponse r = new DeliveryResponse();
        r.setChatMessage("Please sign in to continue — once logged in, I can track your delivery.");
        return ChatbotResponse.<DeliveryResponse>builder()
                .data(r)
                .responseTimeMs(System.currentTimeMillis() - startTime)
                .intent(getIntentType())
                .build();
    }

    private ChatbotResponse<DeliveryResponse> buildResponse(
            ChatResponse response, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;

        DeliveryResponse data;
        try {
            data = deliveryOutputConverter.convert(response.getResult().getOutput().getText());
        } catch (Exception e) {
            log.warn("⚠️ Could not parse DeliveryResponse, wrapping raw text");
            data = new DeliveryResponse();
            data.setChatMessage(response.getResult().getOutput().getText());
        }

        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                response.getMetadata().getUsage(),
                response.getMetadata().getModel()
        );

        trackAnalytics(request, response, responseTime);

        return ChatbotResponse.<DeliveryResponse>builder()
                .data(data)
                .tokenUsage(tokens)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }

    private void trackAnalytics(ChatRequest request, ChatResponse response, long responseTime) {
        var usage = response.getMetadata().getUsage();
        aiAnalyticsService.trackUsage(
                null, null,
                request.getMessage(),
                response.getResult().getOutput().getText(),
                usage != null ? usage.getPromptTokens().intValue() : 0,
                usage != null ? usage.getCompletionTokens().intValue() : 0,
                response.getMetadata().getModel(),
                response.getResult().getMetadata().getFinishReason(),
                true,
                "getDeliveryDetails",
                responseTime
        );
        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);
    }
}
