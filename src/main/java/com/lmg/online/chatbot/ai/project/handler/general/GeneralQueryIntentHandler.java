package com.lmg.online.chatbot.ai.project.handler.general;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.analytics.TokenCostCalculator;
import com.lmg.online.chatbot.ai.analytics.TokenUsage;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Handler for general queries that don't fit other intents
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeneralQueryIntentHandler implements IntentHandler<String> {

    private static final String SUPPORT_PHONE = "1800-123-1555";

    @Autowired
    @Qualifier("generalClient")
    private  ChatClient chatClient;
    private final TokenCostCalculator tokenCostCalculator;
    private final AiAnalyticsService aiAnalyticsService;

    @Override
    public ChatbotResponse<String> handle(ChatRequest request, long startTime) {
        log.info("💬 Handling GENERAL_QUERY intent");

        String prompt = buildPrompt(request.getMessage(),request);
        ChatResponse response = chatClient.prompt()
                .user(prompt)
                .call()
                .chatResponse();

        return buildResponse(response, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "GENERAL_QUERY";
    }

    @Override
    public boolean canHandle(String query) {
        // General handler doesn't do pattern matching - it's the fallback
        return false;
    }

    private String buildPrompt(String query, ChatRequest request) {
        String concept = request.getConcept();
        String phone = ConceptBaseUrlResolver.getPhoneNumber(concept);

        // ✅ Add phone number line only if available
        String contactLine = (phone != null && !phone.isBlank())
                ? String.format("For help, call %s. ", phone)
                : "";

        return String.format(
                "Q: %s\n\n" +
                        "Answer only if the question is about %s (Landmark Stores India) or its online/store services. " +
                        "If the query is about any other brand, company, or unrelated topic, " +
                        "do not answer or explain — just reply with: " +
                        "'😊 Happy shopping with %s! %sEnjoy your experience with us!' " +
                        "Always stay polite, helpful, and focus only on LS-related topics like orders, stores, or policies.",
                query,
                concept,
                concept,
                contactLine
        );
    }




    private ChatbotResponse<String> buildResponse(
            ChatResponse response, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;
        String data = response.getResult().getOutput().getText();

        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                response.getMetadata().getUsage(),
                response.getMetadata().getModel()
        );

        trackAnalytics(request, response, responseTime);

        return ChatbotResponse.<String>builder()
                .data(data)
                .tokenUsage(tokens)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }

    private void trackAnalytics(ChatRequest request, ChatResponse response, long responseTime) {
        var usage = response.getMetadata().getUsage();

        aiAnalyticsService.trackUsage(
                null,
                null,
                request.getMessage(),
                response.getResult().getOutput().getText(),
                usage.getPromptTokens().intValue(),
                usage.getCompletionTokens().intValue(),
                response.getMetadata().getModel(),
                response.getResult().getMetadata().getFinishReason(),
                false,
                "none",
                responseTime
        );

        log.info("📊 {} - Tokens: {} (↑{} ↓{}), Time: {}ms",
                getIntentType(), usage.getTotalTokens(), usage.getPromptTokens(),
                usage.getCompletionTokens(), responseTime);
    }
}