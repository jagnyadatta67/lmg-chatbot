package com.lmg.online.chatbot.ai.project.handler.order;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.order.OrderEntryDetailTool;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderEntryDetailResponse;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles ORDER_ENTRY_INTENT — fetches per-item delivery and return details
 * for a specific order entry when the user clicks "More Details" in the widget.
 *
 * This handler is ALWAYS triggered via {@code intentHint = "ORDER_ENTRY_INTENT"}
 * set by the frontend button — it is never reached through free-text classification.
 * {@code canHandle()} therefore always returns {@code false}.
 *
 * Required ChatRequest fields:
 *   - userId      — must be authenticated
 *   - accessToken — OAuth token for Hybris
 *   - orderNo     — the order number
 *   - entryPk     — the specific entry's primary key (from OrderDetail.orderEntryPk)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEntryIntentHandler implements IntentHandler<OrderEntryDetailResponse> {

    private final OrderEntryDetailTool orderEntryDetailTool;
    private final AiAnalyticsService   aiAnalyticsService;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ChatbotResponse<OrderEntryDetailResponse> handle(ChatRequest request, long startTime) {
        log.info("🔍 Handling ORDER_ENTRY_INTENT — userId={}, orderNo={}, entryPk={}",
                request.getUserId(), request.getOrderNo(), request.getEntryPk());

        // 1. Auth guard
        if (StringUtils.isEmpty(request.getUserId())) {
            log.warn("⚠️ ORDER_ENTRY_INTENT — unauthenticated request");
            OrderEntryDetailResponse r = new OrderEntryDetailResponse();
            r.setChatMessage("Please sign in to view order entry details.");
            return buildResponse(r, request, startTime);
        }

        // 2. Both orderNo + entryPk are mandatory for this call
        if (StringUtils.isEmpty(request.getOrderNo()) || StringUtils.isEmpty(request.getEntryPk())) {
            log.warn("⚠️ ORDER_ENTRY_INTENT — missing orderNo or entryPk");
            OrderEntryDetailResponse r = new OrderEntryDetailResponse();
            r.setChatMessage("Missing order number or item reference. Please tap 'More Details' again.");
            return buildResponse(r, request, startTime);
        }

        // 3. Fetch entry delivery/return details from Hybris
        OrderEntryDetailResponse data = orderEntryDetailTool.getOrderEntryDetail(
                request.getUserId(),
                request.getAccessToken(),
                request.getOrderNo(),
                request.getEntryPk(),
                request.getConcept(),
                request.getEnv(),
                request.getAppid()
        );

        return buildResponse(data, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "ORDER_ENTRY_INTENT";
    }

    /**
     * Always returns false — this intent is only reachable via intentHint (widget button).
     * Free-text queries should never be routed here.
     */
    @Override
    public boolean canHandle(String query) {
        return false;
    }

    // ── Response builder ──────────────────────────────────────────────────────

    private ChatbotResponse<OrderEntryDetailResponse> buildResponse(
            OrderEntryDetailResponse data, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;

        aiAnalyticsService.trackUsage(
                null, null,
                request != null ? request.getMessage() : "",
                data.getChatMessage(),
                0, 0,
                "none",
                "stop",
                false,
                "orderEntryDetailTool",
                responseTime
        );

        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);

        return ChatbotResponse.<OrderEntryDetailResponse>builder()
                .data(data)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }
}
