package com.lmg.online.chatbot.ai.project.handler.order;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.order.OrderTrackingTool;
import com.lmg.online.chatbot.ai.tools.order.dto.ChatbotOrderTrackingResponse;
import com.lmg.online.chatbot.ai.tools.order.dto.HybrisSingleOrderResponse;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Handles ORDER_TRACKING intent — fetches a single order's full details by order number.
 *
 * Flow (no AI involved):
 *   1. Auth guard — userId must be present
 *   2. Extract order number from request.orderNo or via regex from message text
 *   3. No order number → return friendly ask message immediately
 *   4. Call OrderTrackingTool → Hybris order API → HybrisSingleOrderResponse
 *   5. Map HybrisSingleOrderResponse → ChatbotOrderTrackingResponse (single order in orders[0])
 *   6. Return ChatbotResponse directly
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

        // 3. Fetch from Hybris API (pure REST, no AI)
        HybrisSingleOrderResponse hybrisData = orderTrackingTool.getSingleOrderDetails(
                request.getUserId(),
                request.getAccessToken(),
                orderNo,
                request.getConcept(),
                request.getEnv(),
                request.getAppid()
        );

        // 4. Handle null response (API error)
        if (hybrisData == null) {
            ChatbotOrderTrackingResponse errorResp = new ChatbotOrderTrackingResponse();
            errorResp.setChatMessage("Unable to fetch order details right now. "
                    + ConceptBaseUrlResolver.getPhoneNumber(request.getConcept()));
            errorResp.setOrders(Collections.emptyList());
            return buildResponse(errorResp, request, startTime);
        }

        // 5. Map Hybris response → widget-ready DTO
        ChatbotOrderTrackingResponse data;
        try {
            data = mapToTrackingResponse(hybrisData, request.getConcept(), request.getEnv());
        } catch (Exception e) {
            log.error("❌ Failed to map Hybris order response for orderNo={}", orderNo, e);
            ChatbotOrderTrackingResponse errorResp = new ChatbotOrderTrackingResponse();
            errorResp.setChatMessage("Unable to process order details right now. "
                    + ConceptBaseUrlResolver.getPhoneNumber(request.getConcept()));
            errorResp.setOrders(Collections.emptyList());
            return buildResponse(errorResp, request, startTime);
        }

        // 6. Handle empty / unrecognised order
        if (data.getOrders() == null || data.getOrders().isEmpty()) {
            data.setChatMessage("No order found for order number " + orderNo + ". Please check and try again.");
            data.setOrders(Collections.emptyList());
        }

        log.info("✅ Returning order={} status={}", orderNo,
                data.getOrders().isEmpty() ? "none" : data.getOrders().get(0).getOrderStatus());

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

    // ── Mapping ───────────────────────────────────────────────────────────────

    /**
     * Maps a raw {@link HybrisSingleOrderResponse} into the widget-ready
     * {@link ChatbotOrderTrackingResponse}, wrapping the single order in {@code orders[0]}.
     *
     * Field mappings:
     *   code            → orderNo
     *   statusDisplay   → orderStatus  (fallback: status)
     *   created         → orderDate
     *   totalPrice.value → orderAmount
     *   entries[]       → OrderEntry[] (see per-entry mapping below)
     *
     * Per-entry:
     *   entryNumber          → position
     *   orderEntryPk         → orderEntryPk
     *   quantity             → quantity
     *   returnEligible       → returnable
     *   exchangeEnabled      → exchangeable
     *   product.code         → productCode
     *   product.url          → productUrl
     *   product.images[0].url → productImage  (resolved to absolute URL if relative)
     */
    private ChatbotOrderTrackingResponse mapToTrackingResponse(
            HybrisSingleOrderResponse hybris, String concept, String env) {

        ChatbotOrderTrackingResponse response = new ChatbotOrderTrackingResponse();

        if (hybris.getCode() == null || hybris.getCode().isBlank()) {
            // API returned something but without a valid order code
            return response;
        }

        ChatbotOrderTrackingResponse.OrderSummary summary = new ChatbotOrderTrackingResponse.OrderSummary();
        summary.setOrderNo(hybris.getCode());
        summary.setOrderStatus(
                hybris.getStatusDisplay() != null ? hybris.getStatusDisplay() : hybris.getStatus());
        summary.setOrderDate(hybris.getCreated());
        summary.setOrderAmount(hybris.getTotalPrice() != null ? hybris.getTotalPrice().getValue() : null);

        List<HybrisSingleOrderResponse.HybrisEntry> hybrisEntries =
                hybris.getEntries() != null ? hybris.getEntries() : Collections.emptyList();

        // Count distinct active consignment codes → number of shipments
        long shipments = hybrisEntries.stream()
                .map(HybrisSingleOrderResponse.HybrisEntry::getConsignmentStatus)
                .filter(Objects::nonNull)
                .map(HybrisSingleOrderResponse.HybrisEntry.ConsignmentStatusData::getConsignmentCode)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .count();
        summary.setNumberOfShipments((int) shipments);

        // Count entries whose last consignment history event indicates delivery
        long delivered = hybrisEntries.stream()
                .filter(e -> {
                    if (e.getConsignmentStatus() == null) return false;
                    List<HybrisSingleOrderResponse.HybrisEntry.ConsignmentStatusData.ConsignmentHistory> hist =
                            e.getConsignmentStatus().getConsignmentHistoryData();
                    if (hist == null || hist.isEmpty()) return false;
                    String lastStatus = hist.get(hist.size() - 1).getStatus();
                    return lastStatus != null && lastStatus.toUpperCase().contains("DELIVER");
                })
                .count();
        summary.setNumberOfProductsDelivered((int) delivered);

        // Map entries
        List<ChatbotOrderTrackingResponse.OrderEntry> entries = hybrisEntries.stream()
                .map(he -> {
                    ChatbotOrderTrackingResponse.OrderEntry entry =
                            new ChatbotOrderTrackingResponse.OrderEntry();

                    if (he.getEntryNumber() != null) entry.setPosition(he.getEntryNumber());
                    entry.setOrderEntryPk(he.getOrderEntryPk());
                    entry.setQuantity(he.getQuantity() != null ? he.getQuantity() : 1);
                    entry.setReturnable(he.isReturnEligible());
                    entry.setExchangeable(he.isExchangeEnabled());

                    HybrisSingleOrderResponse.HybrisEntry.HybrisProduct product = he.getProduct();
                    if (product != null) {
                        entry.setProductName(product.getName());
                        entry.setProductCode(product.getCode());
                        entry.setProductUrl(product.getUrl());

                        if (product.getImages() != null && !product.getImages().isEmpty()) {
                            String imgUrl = product.getImages().get(0).getUrl();
                            entry.setProductImage(resolveImageUrl(imgUrl, concept, env));
                        }
                    }

                    return entry;
                })
                .collect(Collectors.toList());

        summary.setEntries(entries);
        response.setOrders(Collections.singletonList(summary));
        return response;
    }

    /**
     * Ensures the image URL is absolute.
     * Hybris sometimes returns a relative path (e.g. {@code /medias/sys_master/...}).
     * Prefix it with the concept's environment base URL in that case.
     */
    private String resolveImageUrl(String imageUrl, String concept, String env) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) return imageUrl;
        return ConceptBaseUrlResolver.getEnvBaseUrl(concept, env) + imageUrl;
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
