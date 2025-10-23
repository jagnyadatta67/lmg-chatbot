package com.lmg.online.chatbot.ai.project.handler.giftcard;


import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.analytics.TokenCostCalculator;
import com.lmg.online.chatbot.ai.analytics.TokenUsage;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.giftcard.GiftCardBalanceTool;
import com.lmg.online.chatbot.ai.tools.giftcard.dto.GiftCardBalanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class GiftCardBalanceIntentHandler implements IntentHandler<GiftCardBalanceResponse> {

    private static final Pattern GIFT_CARD_PATTERN = Pattern.compile(
            ".*\\b(gift\\s*card|card\\s*balance|check\\s*balance|giftcard)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final String GIFTCARD_BALANCE_FORMAT = """
Return JSON: {"cardNumber":"string","status":"SUCCESS|FAILED","message":"string",
"balanceAmount":0.0,"currency":"INR","errorOccurred":false,
"errors":[{"message":"lmg.giftcard.card.not.found","reason":"GIFT_CARD_NOT_FOUND",
"subject":"BALANCE ENQUIRY FAILURE FOR CARD","subjectType":"GIFT_CARD_FAILURE",
"type":"BALANCE_ENQUIRY_FAILURE"}]}
""";




    private final ChatClient giftCardClient;
    private final GiftCardBalanceTool giftCardBalanceTool;
    private final TokenCostCalculator tokenCostCalculator;
    private final AiAnalyticsService aiAnalyticsService;
    private final BeanOutputConverter<GiftCardBalanceResponse> giftCardBalanceConverter;

    @Override
    public ChatbotResponse<GiftCardBalanceResponse> handle(ChatRequest request, long startTime) {
        log.info("🎁 Handling GIFT_CARD_BALANCE intent");

        String prompt = buildPrompt(request);
        ChatResponse response = giftCardClient.prompt()
                .user(prompt)
                .tools(giftCardBalanceTool)
                .call()
                .chatResponse();

        return buildResponse(response, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "GIFT_CARD_BALANCE";
    }

    @Override
    public boolean canHandle(String query) {
        return GIFT_CARD_PATTERN.matcher(query.toLowerCase()).matches();
    }

    private String buildPrompt(ChatRequest request) {
        return String.format(
                "%s\nQuery: %s\nCall tool giftCardBalance(concept=%s,env=%s,accessToken=%s,appId=%s,cardNumber=%s,pin=%s) " +
                        "chat_message should be empty if giftCardBalance is used " ,
                GIFTCARD_BALANCE_FORMAT,
                request.getMessage(),
                request.getConcept(),
                request.getEnv(),
                request.getUserId(),
                request.getAppid(),
                request.getCardNumber(),
                request.getPin()
        );
    }

    private ChatbotResponse<GiftCardBalanceResponse> buildResponse(
            ChatResponse response, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;
        GiftCardBalanceResponse data = giftCardBalanceConverter.convert(
                response.getResult().getOutput().getText()
        );

        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                response.getMetadata().getUsage(),
                response.getMetadata().getModel()
        );

        trackAnalytics(request, response, responseTime);

        return ChatbotResponse.<GiftCardBalanceResponse>builder()
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
                "giftCardBalanceTool",
                responseTime
        );

        log.info("📊 {} - Tokens: {} (↑{} ↓{}), Time: {}ms",
                getIntentType(), usage.getTotalTokens(), usage.getPromptTokens(),
                usage.getCompletionTokens(), responseTime);
    }
}