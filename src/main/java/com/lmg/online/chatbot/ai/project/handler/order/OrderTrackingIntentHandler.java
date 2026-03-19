package com.lmg.online.chatbot.ai.project.handler.order;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.order.OrderTrackingTool;
import com.lmg.online.chatbot.ai.tools.order.dto.ChatbotOrderTrackingResponse;
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
 *   4. Call OrderTrackingTool → chatbot REST API → ChatbotOrderTrackingResponse
 *   5. Return ChatbotResponse directly (no mapper needed — new API maps cleanly)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTrackingIntentHandler implements IntentHandler<ChatbotOrderTrackingResponse> {

    /**
     * Fast-path routing: matches obvious order-tracking queries.
     *
     * Two alternatives:
     *
     *   Alt 1 — Explicit tracking keywords (track, order status, where is my order, etc.)
     *            Two negative lookaheads block CANCEL_OR_RETURN territory:
     *              1. Exact action keywords — "cancel / return / exchange / refund"
     *              2. Personal intent phrases — "i want to", "can i", "how do i", etc.
     *                 (catches action-word typos like "canccel", "excahange" via the phrase)
     *
     *   Alt 2 — Bare order number ONLY (message is just the digits, nothing else)
     *            e.g. "9419472006" typed alone → fast-path here.
     *            A number followed by ANY other word (even a typo like "canccel it") does
     *            NOT match this alternative and falls through to the AI classifier, which
     *            correctly resolves the intent despite the typo.
     *
     * Examples that MATCH (route here):
     *   "track order 9419396447", "where is my order", "order status",
     *   "9419472006" (bare number)
     * Examples that do NOT match (route to CANCEL_OR_RETURN or AI classifier):
     *   "9419472006 canccel it"  ← typo, falls to AI → CANCEL_OR_RETURN
     *   "9419472006 i want to excahange"  ← intent phrase + typo, Alt 1 lookahead 2 blocks
     *   "9419472006 i want to cancel"     ← blocked by both lookaheads
     *   "can i return this", "return my order 9419472006"
     */
    private static final Pattern ORDER_PATTERN = Pattern.compile(
            // ── Alt 1: explicit tracking keywords ─────────────────────────────
            // Exclude exact action keywords
            "(?!.*\\b(cancel|return|exchange|refund)\\b)" +
            // Exclude personal intent phrases (catches action-word typos too)
            "(?!.*\\b(i\\s+want\\s+to|i\\s+need\\s+to|i\\s+would\\s+like\\s+to|can\\s+i|how\\s+can\\s+i|how\\s+do\\s+i)\\b)" +
            // Exclude ORDER_LISTING territory — "my order history", "show my orders", etc.
            "(?!.*\\b(history|orders|past\\s+order|all\\s+order|recent\\s+purchase|order\\s+history)\\b)" +
            ".*\\b(track|order\\s*status|where.*order|order.*detail|check.*order|" +
            "my\\s*order\\s+\\d|order\\s+\\d|my\\s+order)\\b.*" +
            // ── Alt 2: bare order number — nothing else in the message ─────────
            "|\\s*\\d{7,12}\\s*",
            Pattern.CASE_INSENSITIVE
    );

    /** Extracts a 7–12 digit numeric order number from free text */
    private static final Pattern ORDER_NUMBER_PATTERN =
            Pattern.compile("\\b(\\d{7,12})\\b");

    private final OrderTrackingTool  orderTrackingTool;
    private final AiAnalyticsService aiAnalyticsService;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ChatbotResponse<ChatbotOrderTrackingResponse> handle(ChatRequest request, long startTime) {
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

        // 3. Fetch from chatbot API (pure REST, no AI, no mapper)
        ChatbotOrderTrackingResponse data = orderTrackingTool.getSingleOrderDetails(
                request.getUserId(),
                request.getAccessToken(),
                orderNo,
                request.getConcept(),
                request.getEnv(),
                request.getAppid()
        );

        // 4. Handle null response (API error)
        if (data == null) {
            ChatbotOrderTrackingResponse errorResp = new ChatbotOrderTrackingResponse();
            errorResp.setChatMessage("Unable to fetch order details right now. "
                    + ConceptBaseUrlResolver.getPhoneNumber(request.getConcept()));
            errorResp.setOrders(Collections.emptyList());
            return buildResponse(errorResp, request, startTime);
        }

        // 5. Handle empty orders list
        if (data.getOrders() == null || data.getOrders().isEmpty()) {
            data.setChatMessage("No order found for order number " + orderNo + ". Please check and try again.");
            data.setOrders(Collections.emptyList());
        }

        log.info("✅ Returning {} order(s) for orderNo={}", data.getOrders().size(), orderNo);

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

    private ChatbotOrderTrackingResponse unauthResponse() {
        ChatbotOrderTrackingResponse r = new ChatbotOrderTrackingResponse();
        r.setChatMessage(
                "Please sign in to continue — once logged in I can fetch your order details.");
        r.setOrders(Collections.emptyList());
        return r;
    }

    private ChatbotOrderTrackingResponse askForOrderNumberResponse() {
        ChatbotOrderTrackingResponse r = new ChatbotOrderTrackingResponse();
        r.setChatMessage(
                "Please share your order number so I can look it up. " +
                "Order numbers are numeric, e.g. 9419396447.");
        r.setNeedsOrderNumber(true);
        r.setOrders(Collections.emptyList());
        return r;
    }

    // ── Response builder ──────────────────────────────────────────────────────

    private ChatbotResponse<ChatbotOrderTrackingResponse> buildResponse(
            ChatbotOrderTrackingResponse data, ChatRequest request, long startTime) {

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
                "orderTrackingTool",
                responseTime
        );

        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);

        return ChatbotResponse.<ChatbotOrderTrackingResponse>builder()
                .data(data)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }
}
