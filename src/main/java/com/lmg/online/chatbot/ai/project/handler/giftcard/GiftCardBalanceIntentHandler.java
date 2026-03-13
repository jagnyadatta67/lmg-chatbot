package com.lmg.online.chatbot.ai.project.handler.giftcard;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.giftcard.GiftCardBalanceTool;
import com.lmg.online.chatbot.ai.tools.giftcard.dto.GiftCardBalanceResponse;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Handles GIFT_CARD_BALANCE intent — direct REST call, zero AI.
 *
 * Flow:
 *   1. Validate card number is present in request
 *   2. Call GiftCardBalanceTool → Hybris POST → GiftCardBalanceResponse
 *   3. Build friendly chat_message from response fields
 *   4. Return ChatbotResponse
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GiftCardBalanceIntentHandler implements IntentHandler<GiftCardBalanceResponse> {

    private static final Pattern GIFT_CARD_PATTERN = Pattern.compile(
            ".*\\b(gift\\s*card|card\\s*balance|check\\s*balance|giftcard|gc\\s*balance)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private final GiftCardBalanceTool  giftCardBalanceTool;
    private final AiAnalyticsService   aiAnalyticsService;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ChatbotResponse<GiftCardBalanceResponse> handle(ChatRequest request, long startTime) {
        log.info("🎁 Handling GIFT_CARD_BALANCE intent");

        // 1. Validate card number
        if (StringUtils.isEmpty(request.getCardNumber())) {
            GiftCardBalanceResponse r = new GiftCardBalanceResponse();
            r.setChatMessage("Please provide your gift card number so I can check the balance.");
            return buildResponse(r, request, startTime);
        }

        // 2. Direct tool call — no AI
        GiftCardBalanceResponse data = giftCardBalanceTool.checkGiftCardBalance(
                request.getConcept(),
                request.getEnv(),
                request.getAccessToken(),
                request.getAppid(),
                request.getCardNumber(),
                request.getPin() != null ? request.getPin() : ""
        );

        // 3. Build friendly message if not already set
        if (StringUtils.isEmpty(data.getChatMessage())) {
            data.setChatMessage(buildChatMessage(data, request));
        }

        return buildResponse(data, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "GIFT_CARD_BALANCE";
    }

    @Override
    public boolean canHandle(String query) {
        return GIFT_CARD_PATTERN.matcher(query).matches();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a customer-facing message from the Hybris response fields.
     * Shows formatted balance + expiry on success, error message on failure.
     */
    private String buildChatMessage(GiftCardBalanceResponse data, ChatRequest request) {
        if (data.hasErrors()) {
            String err = data.firstErrorMessage();
            if (err != null && err.contains("not.found")) {
                return "The gift card number you entered was not found. " +
                       "Please double-check the card number and try again.";
            }
            if (err != null && err.contains("invalid")) {
                return "The gift card details are invalid. Please verify your card number and PIN.";
            }
            return "Unable to retrieve the gift card balance. " +
                   ConceptBaseUrlResolver.getPhoneNumber(request.getConcept());
        }

        if (!data.isActive()) {
            return "This gift card is inactive or has already been used.";
        }

        GiftCardBalanceResponse.AmountData amount = data.getAmount();
        if (amount == null) {
            return "Gift card found but balance information is unavailable.";
        }

        String balance  = amount.getFormattedValue() != null
                ? amount.getFormattedValue()
                : (amount.getCurrencyIso() + " " + amount.getValue());
        String expiry   = data.getExpiryDate() != null
                ? formatExpiry(data.getExpiryDate())
                : "N/A";

        return String.format(
                "Your gift card balance is %s. Valid until %s.", balance, expiry);
    }

    /** Formats ISO expiry date "2027-03-14T03:03:36+0530" → "14 Mar 2027" */
    private String formatExpiry(String isoDate) {
        try {
            // Take only the date part before 'T'
            String datePart = isoDate.split("T")[0];          // "2027-03-14"
            String[] parts  = datePart.split("-");              // ["2027","03","14"]
            String[] months = {"","Jan","Feb","Mar","Apr","May","Jun",
                               "Jul","Aug","Sep","Oct","Nov","Dec"};
            int month = Integer.parseInt(parts[1]);
            return parts[2] + " " + months[month] + " " + parts[0]; // "14 Mar 2027"
        } catch (Exception e) {
            return isoDate;
        }
    }

    // ── Response builder ──────────────────────────────────────────────────────

    private ChatbotResponse<GiftCardBalanceResponse> buildResponse(
            GiftCardBalanceResponse data, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;

        aiAnalyticsService.trackUsage(
                null, null,
                request != null ? request.getMessage() : "",
                data.getChatMessage(),
                0, 0,
                "none",
                "stop",
                false,
                "giftCardBalanceTool",
                responseTime
        );

        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);

        return ChatbotResponse.<GiftCardBalanceResponse>builder()
                .data(data)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }
}
