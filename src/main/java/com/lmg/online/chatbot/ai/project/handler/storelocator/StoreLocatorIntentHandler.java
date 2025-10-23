package com.lmg.online.chatbot.ai.project.handler.storelocator;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.analytics.TokenCostCalculator;
import com.lmg.online.chatbot.ai.analytics.TokenUsage;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.storelocator.StoreLocatorTool;
import com.lmg.online.chatbot.ai.tools.storelocator.dto.StoreList;
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
public class StoreLocatorIntentHandler implements IntentHandler<StoreList> {

    private static final Pattern STORE_PATTERN = Pattern.compile(
            ".*\\b(store|shop|outlet|location|branch|nearest|nearby|address|find store)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final String STORE_FORMAT = """
        Return JSON: {"stores":[{"storeId":"id","storeName":"name","city":"city","address":"addr",
        "contactNumber":"num","workingHours":"hrs","latitude":0.0,"longitude":0.0,"distance":0.0,
        "line1":"l1","line2":"l2","postalCode":"code"}]}
        """;

    private final ChatClient storeLocatorClient;
    private final StoreLocatorTool storeLocatorTool;
    private final TokenCostCalculator tokenCostCalculator;
    private final AiAnalyticsService aiAnalyticsService;
    private final BeanOutputConverter<StoreList> storeLocatorConverter;

    @Override
    public ChatbotResponse<StoreList> handle(ChatRequest request, long startTime) {
        log.info("🏪 Handling STORE_LOCATOR intent");

        String prompt = buildPrompt(request);
        ChatResponse response = storeLocatorClient.prompt()
                .user(prompt)
                .tools(storeLocatorTool)
                .call()
                .chatResponse();

        return buildResponse(response, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "STORE_LOCATOR";
    }

    @Override
    public boolean canHandle(String query) {
        return STORE_PATTERN.matcher(query.toLowerCase()).matches();
    }

    private String buildPrompt(ChatRequest request) {
        return String.format(
                """
                Return only valid JSON in this exact format — no markdown, no extra text:
                %s
        
                Query: %s
                Call storeLocatorTool(concept=%s,env=%s,lat=%s,lng=%s)
                """,
                STORE_FORMAT.trim(),
                request.getMessage(),
                request.getConcept(),
                request.getEnv(),
                request.getLatitude(),
                request.getLongitude()
        );
    }

    private ChatbotResponse<StoreList> buildResponse(
            ChatResponse response, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;
        StoreList data = storeLocatorConverter.convert(
                response.getResult().getOutput().getText()
        );

        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                response.getMetadata().getUsage(),
                response.getMetadata().getModel()
        );

        trackAnalytics(request, response, responseTime);

        return ChatbotResponse.<StoreList>builder()
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
                true,
                "storeLocatorTool",
                responseTime
        );

        log.info("📊 {} - Tokens: {} (↑{} ↓{}), Time: {}ms",
                getIntentType(), usage.getTotalTokens(), usage.getPromptTokens(),
                usage.getCompletionTokens(), responseTime);
    }
}