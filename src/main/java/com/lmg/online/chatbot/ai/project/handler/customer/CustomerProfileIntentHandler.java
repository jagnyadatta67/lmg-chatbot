package com.lmg.online.chatbot.ai.project.handler.customer;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.analytics.TokenCostCalculator;
import com.lmg.online.chatbot.ai.analytics.TokenUsage;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.user.MyProfileDetailsTool;
import com.lmg.online.chatbot.ai.tools.user.dto.CustomerProfileResponseDTO;
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
public class CustomerProfileIntentHandler implements IntentHandler<CustomerProfileResponseDTO> {

    private static final Pattern CUSTOMER_PROFILE_PATTERN = Pattern.compile(
            ".*\\b(profile|my\\s*profile|account|my\\s*account|personal\\s*details|my\\s*details|" +
                    "about\\s*me|user\\s*info|my\\s*info|update\\s*profile|edit\\s*profile)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final String CUSTOMER_PROFILE_FORMAT = """
        Return JSON: {
            "chat_message": "text", 
            "customerProfile": {
                "name": "full name",
                "firstName": "first name",
                "lastName": "last name",
                "email": "email address",
                "mobileNo": "phone number",
                "uid": "unique user id",
                "gender": "MALE/FEMALE/OTHER",
                "loyaltyCardNumber": "card number",
                "signInMobile": "+91xxxxxxxxxx",
                "defaultAddress": {
                    "addressType": "Home/Office",
                    "firstName": "address first name",
                    "email": "address email",
                    "cellphone": "mobile number",
                    "line1": "address line 1",
                    "line2": "address line 2",
                    "landmark": "landmark text",
                    "town": "city/town",
                    "region": {"name": "KARNATAKA"},
                    "country": {"name": "India"},
                    "postalCode": "560037"
                }
            }
        }
        """;

    private static final String LOGIN_FORMAT = """
        Anonymous user, for profile check please login to your account. If this message we will receive then 
        response must exact as 
        "chat_message": Please sign in to continue — once you're logged in, I can fetch your latest details.
        """;

    private final ChatClient customerProfileClient;
    private final MyProfileDetailsTool myProfileDetailsTool;
    private final TokenCostCalculator tokenCostCalculator;
    private final AiAnalyticsService aiAnalyticsService;
    private final BeanOutputConverter<CustomerProfileResponseDTO> profileOutputConverter;

    @Override
    public ChatbotResponse<CustomerProfileResponseDTO> handle(ChatRequest request, long startTime) {
        log.info("👤 Handling CUSTOMER_PROFILE intent");

        boolean isAuthenticated = isUserAuthenticated(request);
        ChatResponse response = isAuthenticated
                ? handleAuthenticatedRequest(request)
                : handleUnauthenticatedRequest(request);

        return buildResponse(response, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "CUSTOMER_PROFILE";
    }

    @Override
    public boolean canHandle(String query) {
        return CUSTOMER_PROFILE_PATTERN.matcher(query.toLowerCase()).matches();
    }

    private boolean isUserAuthenticated(ChatRequest request) {
        return request.getUserId() != null && !request.getUserId().isEmpty();
    }

    private ChatResponse handleAuthenticatedRequest(ChatRequest request) {
        String prompt = String.format(
                "%s\nQuery: %s\nCall myProfileDetailsTool(concept=%s,env=%s,userId=%s,appid=%s) " +
                        "chat_message should empty if myProfileDetailsTool used " + LOGIN_FORMAT,
                CUSTOMER_PROFILE_FORMAT,
                request.getMessage(),
                request.getConcept(),
                request.getEnv(),
                request.getUserId(),
                request.getAppid()
        );

        return customerProfileClient.prompt()
                .user(prompt)
                .tools(myProfileDetailsTool)
                .call()
                .chatResponse();
    }

    private ChatResponse handleUnauthenticatedRequest(ChatRequest request) {
        String prompt = CUSTOMER_PROFILE_FORMAT +
                "\nUser not logged in asking about profile. " +
                "Response: 'Please log in to view profile details.'";

        return customerProfileClient.prompt()
                .user(prompt)
                .call()
                .chatResponse();
    }

    private ChatbotResponse<CustomerProfileResponseDTO> buildResponse(
            ChatResponse response, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;
        CustomerProfileResponseDTO data = profileOutputConverter.convert(
                response.getResult().getOutput().getText()
        );

        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                response.getMetadata().getUsage(),
                response.getMetadata().getModel()
        );

        trackAnalytics(request, response, responseTime);

        return ChatbotResponse.<CustomerProfileResponseDTO>builder()
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
                "myProfileDetailsTool",
                responseTime
        );

        log.info("📊 {} - Tokens: {} (↑{} ↓{}), Time: {}ms",
                getIntentType(), usage.getTotalTokens(), usage.getPromptTokens(),
                usage.getCompletionTokens(), responseTime);
    }
}