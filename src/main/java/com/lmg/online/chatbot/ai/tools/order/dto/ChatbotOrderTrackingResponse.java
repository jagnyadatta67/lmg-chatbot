package com.lmg.online.chatbot.ai.tools.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level DTO for the new chatbot order-tracking API:
 *   GET /chatbot/user/{userId}/order?appId=...&orderNo=...
 *
 * Response shape:
 * <pre>
 * {
 *   "customerId": "user@domain.com",
 *   "orders": [
 *     {
 *       "orderNo": "9419047049",
 *       "orderStatus": "Placed",
 *       "orderDate": "2026-02-12T22:47:34+0530",
 *       "orderAmount": 668.2,
 *       "numberOfShipments": 2,
 *       "numberOfProductsDelivered": 1,
 *       "entries": [ ... ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * {@code chat_message} and {@code needsOrderNumber} are NOT part of the API
 * response — they are set by the handler before returning to the frontend.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatbotOrderTrackingResponse {

    /** Set by handler: friendly error / prompt text for the frontend */
    @JsonProperty("chat_message")
    private String chatMessage;

    /**
     * Set by handler: when true the frontend opens an order-number input
     * so the user can provide a number and retry ORDER_TRACKING.
     */
    @JsonProperty("needsOrderNumber")
    private boolean needsOrderNumber;

    /** Customer's user ID e.g. "user@landmarkgroup.in" */
    @JsonProperty("customerId")
    private String customerId;

    /** List of orders — for a single-order query this will contain exactly one entry */
    @JsonProperty("orders")
    private List<OrderSummary> orders;

    // ─────────────────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderSummary {

        @JsonProperty("orderNo")
        private String orderNo;

        /** Human-readable status e.g. "Placed", "Delivered" */
        @JsonProperty("orderStatus")
        private String orderStatus;

        /** ISO timestamp e.g. "2026-02-12T22:47:34+0530" */
        @JsonProperty("orderDate")
        private String orderDate;

        @JsonProperty("orderAmount")
        private Double orderAmount;

        @JsonProperty("numberOfShipments")
        private int numberOfShipments;

        @JsonProperty("numberOfProductsDelivered")
        private int numberOfProductsDelivered;

        @JsonProperty("entries")
        private List<OrderEntry> entries;
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderEntry {

        @JsonProperty("orderEntryPk")
        private String orderEntryPk;

        @JsonProperty("position")
        private int position;

        @JsonProperty("productCode")
        private String productCode;

        /** Absolute CDN URL for the product thumbnail */
        @JsonProperty("productImage")
        private String productImage;

        /** Relative product page path e.g. "/Girls/Indian-Wear/.../p/1000008989705" */
        @JsonProperty("productUrl")
        private String productUrl;

        @JsonProperty("quantity")
        private int quantity;

        @JsonProperty("returnable")
        private boolean returnable;

        @JsonProperty("exchangeable")
        private boolean exchangeable;
    }
}
