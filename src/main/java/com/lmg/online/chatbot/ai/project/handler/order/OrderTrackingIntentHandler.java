package com.lmg.online.chatbot.ai.project.handler.order;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.order.OrderTrackingTool;
import com.lmg.online.chatbot.ai.tools.order.dto.HybrisSingleOrderResponse;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderResponse;
import com.lmg.online.chatbot.ai.tools.order.helper.HybrisOrderMapper;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles ORDER_TRACKING intent — fetches a single order's details by order number.
 *
 * Flow (no AI involved):
 *   1. Auth guard — userId must be present
 *   2. Extract order number from request.orderNo or via regex from message text
 *   3. No order number → return friendly ask message immediately
 *   4. Call OrderTrackingTool → Hybris REST → HybrisSingleOrderResponse
 *   5. HybrisOrderMapper converts DTO → OrderResponse (direct field mapping)
 *   6. Return ChatbotResponse
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTrackingIntentHandler implements IntentHandler<OrderResponse> {

    /** Fast-path routing: matches obvious order-tracking queries */
    private static final Pattern ORDER_PATTERN = Pattern.compile(
            ".*\\b(track|order\\s*status|where.*order|order.*detail|check.*order|" +
            "my\\s*order\\s+\\d|order\\s+\\d|\\d{7,12})\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    /** Extracts a 7–12 digit numeric order number from free text */
    private static final Pattern ORDER_NUMBER_PATTERN =
            Pattern.compile("\\b(\\d{7,12})\\b");

    private final OrderTrackingTool  orderTrackingTool;
    private final AiAnalyticsService aiAnalyticsService;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ChatbotResponse<OrderResponse> handle(ChatRequest request, long startTime) {
        log.info("📦 Handling ORDER_TRACKING, userId={}", request.getUserId());

        // 1. Auth guard
        if (!isAuthenticated(request)) {
            return buildResponse(unauthResponse(), request, startTime);
        }

        // 2. Extract order number (widget field first, then free-text regex)
        String orderNo = resolveOrderNumber(request);
        if (orderNo == null) {
            log.info("🔢 No order number in message — prompting customer");
            return buildResponse(askForOrderNumberResponse(), request, startTime);
        }

        log.info("🔢 Order number: {}", orderNo);

        // 3. Fetch from Hybris (pure REST, no AI)
        HybrisSingleOrderResponse hybrisData = orderTrackingTool.getSingleOrderDetails(
                request.getUserId(),
                request.getAccessToken(),
                orderNo,
                request.getConcept(),
                request.getEnv(),
                request.getAppid()
        );

        // 4. Map directly to OrderResponse
        OrderResponse data = HybrisOrderMapper.toOrderResponse(
                hybrisData, request.getConcept(), request.getEnv());

        log.info("✅ Mapped {} items for orderNo={}",
                data.getOrderDetailsList() != null ? data.getOrderDetailsList().size() : 0, orderNo);

        return buildResponse(data, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "ORDER_TRACKING";
    }

    @Override
    public boolean canHandle(String query) {
        return ORDER_PATTERN.matcher(query).matches();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isAuthenticated(ChatRequest request) {
        return StringUtils.isNotEmpty(request.getUserId())
                && !request.getUserId().trim().isEmpty();
    }

    /**
     * Resolves the order number from:
     * 1. {@code request.orderNo} — explicit widget field (7–12 digits validated)
     * 2. Regex over {@code request.message} — fallback for free-text input
     */
    private String resolveOrderNumber(ChatRequest request) {
        if (StringUtils.isNotEmpty(request.getOrderNo())) {
            String candidate = request.getOrderNo().trim();
            if (candidate.matches("\\d{7,12}")) return candidate;
        }
        if (StringUtils.isNotEmpty(request.getMessage())) {
            Matcher m = ORDER_NUMBER_PATTERN.matcher(request.getMessage());
            if (m.find()) return m.group(1);
        }
        return null;
    }

    // ── Pre-built responses ───────────────────────────────────────────────────

    private OrderResponse unauthResponse() {
        OrderResponse r = new OrderResponse();
        r.setChat_message(
                "Please sign in to continue — once logged in I can fetch your order details.");
        r.setOrderDetailsList(Collections.emptyList());
        return r;
    }

    private OrderResponse askForOrderNumberResponse() {
        OrderResponse r = new OrderResponse();
        r.setChat_message(
                "Please share your order number so I can look it up. " +
                "Order numbers are numeric, e.g. 9419396447.");
        r.setOrderDetailsList(Collections.emptyList());
        return r;
    }

    // ── Response builder ──────────────────────────────────────────────────────

    private ChatbotResponse<OrderResponse> buildResponse(
            OrderResponse data, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;

        aiAnalyticsService.trackUsage(
                data.getCustomerName(),
                data.getMobileNo(),
                request != null ? request.getMessage() : "",
                data.getChat_message(),
                0, 0,
                "none",
                "stop",
                false,
                "orderTrackingTool",
                responseTime
        );

        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);

        return ChatbotResponse.<OrderResponse>builder()
                .data(data)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }
}
