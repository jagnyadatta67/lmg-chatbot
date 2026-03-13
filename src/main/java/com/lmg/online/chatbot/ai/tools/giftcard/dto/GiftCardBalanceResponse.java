package com.lmg.online.chatbot.ai.tools.giftcard.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Maps the actual Hybris gift-card balance response:
 *
 * Success:
 * {
 *   "type": "lmgGiftCardBalanceResponseDataWsDTO",
 *   "errorOccurred": false,
 *   "active": true,
 *   "amount": { "currencyIso": "INR", "formattedValue": "₹ 1,500.00", "value": 1500.0 },
 *   "expiryDate": "2027-03-14T03:03:36+0530"
 * }
 *
 * Error:
 * {
 *   "errorOccurred": true,
 *   "errors": [{ "message": "lmg.giftcard.card.not.found", "reason": "...", "type": "..." }]
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GiftCardBalanceResponse {

    /** Whether an error occurred on the Hybris side */
    @JsonProperty("errorOccurred")
    private boolean errorOccurred;

    /** Whether the gift card is active */
    @JsonProperty("active")
    private boolean active;

    /** Available balance — the key field shown to the customer */
    @JsonProperty("amount")
    private AmountData amount;

    /** Gift card expiry date e.g. "2027-03-14T03:03:36+0530" */
    @JsonProperty("expiryDate")
    private String expiryDate;

    /** Populated on API error — list of Hybris error objects */
    @JsonProperty("errors")
    private List<GiftCardError> errors;

    /** Set by tool/handler on fatal errors — shown directly to customer */
    @JsonProperty("chat_message")
    private String chatMessage;

    // ── Nested ────────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AmountData {

        @JsonProperty("currencyIso")
        private String currencyIso;

        @JsonProperty("value")
        private Double value;

        /** Pre-formatted e.g. "₹ 1,500.00" — use this directly in UI */
        @JsonProperty("formattedValue")
        private String formattedValue;
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    public boolean hasErrors() {
        return errorOccurred || (errors != null && !errors.isEmpty());
    }

    public String firstErrorMessage() {
        if (errors != null && !errors.isEmpty()) return errors.get(0).getMessage();
        return null;
    }
}
