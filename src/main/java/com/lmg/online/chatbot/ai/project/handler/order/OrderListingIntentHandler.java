package com.lmg.online.chatbot.ai.project.handler.order;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.order.OrderListingTool;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderListResponse;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Handles ORDER_LISTING intent — fetches the customer's full order history.
 *
 * Triggered by queries like:
 *   "my orders", "orders", "order history", "recent orders",
 *   "show my orders", "what did I buy", "past purchases" etc.
 *
 * No order number is required (unlike ORDER_TRACKING).
 * Auth is required — guests are asked to sign in.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderListingIntentHandler implements IntentHandler<OrderListResponse> {

    /**
     * Fast-path pattern — catches common short-form queries WITHOUT a specific order number.
     * Deliberately simple: if the user types just "orders" or "my orders" this fires
     * before the AI classifier is ever called.
     */
    private static final Pattern ORDER_LIST_PATTERN = Pattern.compile(
            ".*\\b(order\\s*histor(y|ies)|my\\s*orders?|show\\s*(my\\s*)?orders?|" +
            "order\\s*list|recent\\s*orders?|past\\s*(orders?|purchases?)|" +
            "order\\s*summar(y|ies)|all\\s*orders?|view\\s*(my\\s*)?orders?|" +
            "what\\s*(did\\s*i\\s*(buy|order)|have\\s*i\\s*(bought|ordered))|" +
            "my\\s*purchases?|purchase\\s*histor(y|ies))\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private final OrderListingTool   orderListingTool;
    private final AiAnalyticsService aiAnalyticsService;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ChatbotResponse<OrderListResponse> handle(ChatRequest request, long startTime) {
        log.info("📋 Handling ORDER_LISTING, userId={}", request.getUserId());

        // 1. Auth guard
        if (!isAuthenticated(request)) {
            return buildResponse(unauthResponse(), request, startTime);
        }

        // 2. Fetch from Hybris (pure REST, no AI)
        OrderListResponse data = orderListingTool.getOrderList(
                request.getUserId(),
                request.getAccessToken(),
                request.getConcept(),
                request.getEnv(),
                request.getAppid(),
                10,      // default page size
                1,       // first page
                "0"      // all orders
        );

        if (data == null) {
            OrderListResponse fallback = new OrderListResponse();
            fallback.setChatMessage("Unable to fetch your order history right now. Please try again.");
            return buildResponse(fallback, request, startTime);
        }

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isAuthenticated(ChatRequest request) {
        return StringUtils.isNotEmpty(request.getUserId())
                && !request.getUserId().trim().isEmpty();
    }

    private OrderListResponse unauthResponse() {
        OrderListResponse r = new OrderListResponse();
        r.setChatMessage("Please sign in to view your order history — " +
                         "once logged in I can show all your past orders.");
        return r;
    }

    private ChatbotResponse<OrderListResponse> buildResponse(
            OrderListResponse data, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;

        aiAnalyticsService.trackUsage(
                null,
                null,
                request != null ? request.getMessage() : "",
                data.getChatMessage(),
                0, 0,
                "none",
                "stop",
                false,
                "orderListingTool",
                responseTime
        );

        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);

        return ChatbotResponse.<OrderListResponse>builder()
                .data(data)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }
}
