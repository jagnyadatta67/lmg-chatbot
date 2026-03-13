package com.lmg.online.chatbot.ai.project.handler.returnstatus;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.analytics.TokenCostCalculator;
import com.lmg.online.chatbot.ai.analytics.TokenUsage;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.returnstatus.ReturnStatusTool;
import com.lmg.online.chatbot.ai.tools.returnstatus.dto.ReturnStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Handles RETURN_STATUS intent.
 *
 * Uses AI with ReturnStatusTool so LLM can extract orderNo + rmaNo
 * from natural language and call the tool.
 *
 * Triggered by: "what's my refund status", "check my return 10221001",
 *               "return pickup status", "when will I get my refund"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnStatusIntentHandler implements IntentHandler<ReturnStatusResponse> {

    private static final Pattern RETURN_PATTERN = Pattern.compile(
            ".*\\b(return|refund|rma|pickup.*return|return.*status|" +
            "money.*back|refund.*status|return.*pickup|exchange.*status)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final String RETURN_SYSTEM_PROMPT = """
            You are a returns and refunds assistant for %s (Landmark Group India).
            The customer is asking about their return or refund status.
            Call getReturnStatus() with the orderNo and rmaNo extracted from the message.
            If orderNo or rmaNo are missing, politely ask the customer to provide them.
            Present the return status clearly — mention the refund amount if available.
            """;

    @Qualifier("returnStatusClient")
    private final ChatClient returnStatusClient;

    private final ReturnStatusTool returnStatusTool;
    private final TokenCostCalculator tokenCostCalculator;
    private final AiAnalyticsService aiAnalyticsService;
    private final BeanOutputConverter<ReturnStatusResponse> returnOutputConverter;

    @Override
    public ChatbotResponse<ReturnStatusResponse> handle(ChatRequest request, long startTime) {
        log.info("↩️ Handling RETURN_STATUS intent");

        if (!isUserAuthenticated(request)) {
            return unauthenticatedResponse(startTime);
        }

        String systemPrompt = String.format(RETURN_SYSTEM_PROMPT, request.getConcept());

        String userMessage = String.format(
                "Customer ID: %s | Access Token: %s | Concept: %s | Env: %s | AppId: %s\nQuery: %s",
                request.getUserId(),
                request.getAccessToken(),
                request.getConcept(),
                request.getEnv(),
                request.getAppid(),
                request.getMessage()
        );

        ChatResponse response = returnStatusClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .tools(returnStatusTool)
                .call()
                .chatResponse();

        return buildResponse(response, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "RETURN_STATUS";
    }

    @Override
    public boolean canHandle(String query) {
        return RETURN_PATTERN.matcher(query).matches();
    }

    private boolean isUserAuthenticated(ChatRequest request) {
        return request.getUserId() != null && !request.getUserId().isBlank();
    }

    private ChatbotResponse<ReturnStatusResponse> unauthenticatedResponse(long startTime) {
        ReturnStatusResponse r = new ReturnStatusResponse();
        r.setChatMessage("Please sign in — once logged in, I can check your return and refund status.");
        return ChatbotResponse.<ReturnStatusResponse>builder()
                .data(r)
                .responseTimeMs(System.currentTimeMillis() - startTime)
                .intent(getIntentType())
                .build();
    }

    private ChatbotResponse<ReturnStatusResponse> buildResponse(
            ChatResponse response, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;

        ReturnStatusResponse data;
        try {
            data = returnOutputConverter.convert(response.getResult().getOutput().getText());
        } catch (Exception e) {
            log.warn("⚠️ Could not parse ReturnStatusResponse, wrapping raw text");
            data = new ReturnStatusResponse();
            data.setChatMessage(response.getResult().getOutput().getText());
        }

        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                response.getMetadata().getUsage(),
                response.getMetadata().getModel()
        );

        trackAnalytics(request, response, responseTime);

        return ChatbotResponse.<ReturnStatusResponse>builder()
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
                "getReturnStatus",
                responseTime
        );
        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);
    }
}
