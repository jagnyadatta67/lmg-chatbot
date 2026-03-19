package com.lmg.online.chatbot.ai.project.handler.returnstatus;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.returnstatus.ReturnStatusTool;
import com.lmg.online.chatbot.ai.tools.returnstatus.dto.ReturnStatusResponse;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles RETURN_STATUS_DETAIL intent — fetches return/refund status for a specific
 * RMA directly from the widget's "Return Status" button on an entry detail card.
 *
 * Unlike {@link ReturnStatusIntentHandler} (which uses AI to extract orderNo/rmaNo
 * from free-text), this handler reads them directly from {@link ChatRequest#orderNo}
 * and {@link ChatRequest#rmaNo} — zero AI token cost.
 *
 * Always triggered via {@code intentHint = "RETURN_STATUS_DETAIL"} from the widget.
 * {@code canHandle()} always returns {@code false} — never reached via free text.
 *
 * Required ChatRequest fields:
 *   - userId      — must be authenticated
 *   - accessToken — OAuth token for Hybris
 *   - orderNo     — the order number (from returnDetail in ORDER_ENTRY_INTENT response)
 *   - rmaNo       — the RMA number   (from returnDetail.rma)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnStatusDetailIntentHandler implements IntentHandler<ReturnStatusResponse> {

    private final ReturnStatusTool   returnStatusTool;
    private final AiAnalyticsService aiAnalyticsService;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ChatbotResponse<ReturnStatusResponse> handle(ChatRequest request, long startTime) {
        log.info("↩️ Handling RETURN_STATUS_DETAIL — userId={}, orderNo={}, rmaNo={}",
                request.getUserId(), request.getOrderNo(), request.getRmaNo());

        // 1. Auth guard
        if (StringUtils.isEmpty(request.getUserId())) {
            log.warn("⚠️ RETURN_STATUS_DETAIL — unauthenticated request");
            return buildResponse(errorResponse("Please sign in to check your return status."), request, startTime);
        }

        // 2. Both orderNo + rmaNo are mandatory — they come from the entry detail card
        if (StringUtils.isEmpty(request.getOrderNo()) || StringUtils.isEmpty(request.getRmaNo())) {
            log.warn("⚠️ RETURN_STATUS_DETAIL — missing orderNo or rmaNo");
            return buildResponse(errorResponse("Missing order or RMA reference. Please tap 'Return Status' again."), request, startTime);
        }

        // 3. Call tool directly — no AI needed, params are already known
        ReturnStatusResponse data = returnStatusTool.getReturnStatus(
                request.getUserId(),
                request.getAccessToken(),
                request.getOrderNo(),
                request.getRmaNo(),
                request.getConcept(),
                request.getEnv(),
                request.getAppid()
        );

        return buildResponse(data, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "RETURN_STATUS_DETAIL";
    }

    /**
     * Always returns false — only reachable via intentHint from the widget button.
     * Free-text return status queries go to {@link ReturnStatusIntentHandler}.
     */
    @Override
    public boolean canHandle(String query) {
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReturnStatusResponse errorResponse(String message) {
        ReturnStatusResponse r = new ReturnStatusResponse();
        r.setChatMessage(message);
        return r;
    }

    private ChatbotResponse<ReturnStatusResponse> buildResponse(
            ReturnStatusResponse data, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;

        aiAnalyticsService.trackUsage(
                null, null,
                request != null ? request.getMessage() : "",
                data.getChatMessage(),
                0, 0,
                "none",
                "stop",
                false,
                "returnStatusTool",
                responseTime
        );

        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);

        return ChatbotResponse.<ReturnStatusResponse>builder()
                .data(data)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }
}
