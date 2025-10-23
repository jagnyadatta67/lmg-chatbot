package com.lmg.online.chatbot.ai.project.handler.general;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.analytics.TokenCostCalculator;
import com.lmg.online.chatbot.ai.analytics.TokenUsage;
import com.lmg.online.chatbot.ai.project.doc.vector.MultiTenantSmartChatService;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyIntentHandler implements IntentHandler<String> {
    private final MultiTenantSmartChatService multiTenantSmartChatService;

    private static final Pattern POLICY_QUESTION_PATTERN = Pattern.compile(
            ".*\\b(" +
                    "policy|return|refund|exchange|cancel|cancellation|replace|replacement|" +
                    "shipping|delivery\\s*charges|delivery\\s*policy|return\\s*policy|" +
                    "exchange\\s*policy|refund\\s*policy|cancel\\s*policy|" +
                    "how\\s*to\\s*(return|cancel|exchange)|" +
                    "when\\s*will\\s*I\\s*get\\s*refund|" +
                    "charges\\s*for\\s*delivery|free\\s*shipping|" +
                    "return\\s*window|refund\\s*time|order\\s*cancel|" +
                    "modify\\s*order|replace\\s*item" +
                    ")\\b.*",
            Pattern.CASE_INSENSITIVE
    );



    private final TokenCostCalculator tokenCostCalculator;
    private final AiAnalyticsService aiAnalyticsService;


    @Override
    public ChatbotResponse<String> handle(ChatRequest req, long startTime) {
        log.info("📋 POLICY_QUESTION");
        ChatResponse response = multiTenantSmartChatService.handlePolicyQuestion(req);
        return buildResponse(response, req, startTime);
    }

    @Override
    public String getIntentType() {
        return "POLICY_QUESTION";
    }

    @Override
    public boolean canHandle(String query) {
        return POLICY_QUESTION_PATTERN.matcher(query.toLowerCase()).matches();
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
                getIntentType(),
                getIntentType(),
                request.getMessage(),
                response.getResult().getOutput().getText(),
                usage.getPromptTokens().intValue(),
                usage.getCompletionTokens().intValue(),
                response.getMetadata().getModel(),
                response.getResult().getMetadata().getFinishReason(),
                true,
                "policySearch",
                responseTime
        );

        log.info("📊 {} - Tokens: {} (↑{} ↓{}), Time: {}ms",
                getIntentType(), usage.getTotalTokens(), usage.getPromptTokens(),
                usage.getCompletionTokens(), responseTime);
    }
}