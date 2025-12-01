package com.lmg.online.chatbot.ai.project.handler.order;



import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.analytics.TokenCostCalculator;
import com.lmg.online.chatbot.ai.analytics.TokenUsage;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.order.OrderTrackingTool;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderResponse;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTrackingIntentHandler implements IntentHandler<OrderResponse> {

    private static final Pattern ORDER_PATTERN = Pattern.compile(
            ".*\\b(order|track|delivery|shipment|status|where.*order)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final String ORDER_FORMAT = """
        Return JSON: {"chat_message":"text","customerName":"name","mobileNo":"phone",
        "orderDetailsList":[{"orderAmount":0,"orderDate":"date","orderNo":"num","orderStatus":"status",
        "totalProducts":0,"productName":"name","imageURL":"url","productURL":"url","netAmount":"amt",
        "color":"col","size":"sz","qty":"q","tat":"t","estmtDate":"date","latestStatus":"st",
        "returnAllow":false,"exchangeAllow":false,"exchangeDay":"days"}]}
        """;

    private static final String LOGIN_FORMAT = """
        Anonymous user, for order check please login to your account. If this message we will receive then 
        response must exact as 
        "chat_message": Please sign in to continue — once you're logged in, I can fetch your latest details.
        """;

    private final ChatClient orderTrackClient;
    private final OrderTrackingTool orderTrackingTool;
    private final TokenCostCalculator tokenCostCalculator;
    private final AiAnalyticsService aiAnalyticsService;
    private final BeanOutputConverter<OrderResponse> orderOutputConverter;
    private final ObjectMapper objectMapper;

    @Override
    public ChatbotResponse<OrderResponse> handle(ChatRequest request, long startTime) {


        boolean isAuthenticated = isUserAuthenticated(request);
        log.info("📦 Handling ORDER_TRACKING intent for isAuthenticated {} ",isAuthenticated);
        ChatResponse response = isAuthenticated
                ? handleAuthenticatedRequest(request)
                : handleUnauthenticatedRequest(request);

        return buildResponse(response, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "ORDER_TRACKING";
    }

    @Override
    public boolean canHandle(String query) {
        return ORDER_PATTERN.matcher(query.toLowerCase()).matches();
    }

    private boolean isUserAuthenticated(ChatRequest request) {

        return StringUtils.isNotEmpty(request.getUserId().trim());
    }

    private ChatResponse handleAuthenticatedRequest(ChatRequest request) {
        String prompt = String.format(
                """
                Query: %s
                Call: orderTrackingTool(concept="%s", env="%s", userId="%s", appid="%s")
                
                Reply ONLY in this JSON format:
                 %s
                """,
                request.getMessage(),
                request.getConcept(),
                request.getEnv(),
                request.getUserId().trim(),
                request.getAppid(),
                ORDER_FORMAT);
        OrderResponse response=orderTrackingTool.getOrderStatus(request.getUserId(),request.getConcept(),request.getEnv(),request.getAppid());
        return buildChatResponse(response,request);
    }




    /**
     * Build ChatResponse from OrderResponse
     * Choose one of the methods below based on your needs:
     */

    // Method 1: Return as JSON string (if frontend expects JSON)
    private ChatResponse buildChatResponse(OrderResponse orderResponse, ChatRequest request) {
        try {
            String jsonContent = objectMapper.writeValueAsString(orderResponse);
            return createChatResponse(jsonContent);
        } catch (Exception e) {
            log.error("Error serializing order response", e);
            return buildErrorChatResponse("Failed to format order data",request);
        }
    }

    /**
     * Core method to create ChatResponse from content string
     */
    private ChatResponse createChatResponse(String content) {
        AssistantMessage message = new AssistantMessage(content);
        Generation generation = new Generation(message);

        return new ChatResponse(
                List.of(generation),
                ChatResponseMetadata.builder().build()
        );
    }

    /**
     * Build error ChatResponse
     */
    private ChatResponse buildErrorChatResponse(String errorMessage, ChatRequest request) {
        String content = String.format(
                "I'm sorry, I couldn't retrieve your orders at this time. Please contact our customer care for more details:" +
                       ConceptBaseUrlResolver.getPhoneNumber(request.getConcept()),
                errorMessage
        );
        return createChatResponse(content);
    }

    private ChatResponse handleUnauthenticatedRequest(ChatRequest request) {
        String prompt = ORDER_FORMAT +
                "\n User not logged in asking about orders. " +
                "Response: 'Please log in to view order details.' Set orderDetailsList=[]";

        return orderTrackClient.prompt()
                .user(prompt)
                .call()
                .chatResponse();
    }

    private ChatbotResponse<OrderResponse> buildResponse(
            ChatResponse response, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;
        OrderResponse data = orderOutputConverter.convert(
                response.getResult().getOutput().getText()
        );

        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                response.getMetadata().getUsage(),
                response.getMetadata().getModel()
        );

        trackAnalytics(request, data, response, responseTime);

        return ChatbotResponse.<OrderResponse>builder()
                .data(data)
                .tokenUsage(tokens)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }

    private void trackAnalytics(ChatRequest request, OrderResponse data,
                                ChatResponse response, long responseTime) {
        var usage = response.getMetadata().getUsage();

        aiAnalyticsService.trackUsage(
                data.getCustomerName(),
                data.getMobileNo(),
                request.getMessage(),
                response.getResult().getOutput().getText(),
                usage.getPromptTokens().intValue(),
                usage.getCompletionTokens().intValue(),
                response.getMetadata().getModel(),
                response.getResult().getMetadata().getFinishReason(),
                true,
                "orderTrackingTool",
                responseTime
        );

        log.info("📊 {} - Tokens: {} (↑{} ↓{}), Time: {}ms",
                getIntentType(), usage.getTotalTokens(), usage.getPromptTokens(),
                usage.getCompletionTokens(), responseTime);
    }
}