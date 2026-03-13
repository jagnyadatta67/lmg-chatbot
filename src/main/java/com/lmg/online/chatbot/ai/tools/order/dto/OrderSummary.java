package com.lmg.online.chatbot.ai.tools.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single order entry in the order listing API response.
 *
 * Maps to the Hybris Commerce v2 /en/users/{userId}/v2/orders entry.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderSummary {

    /** Hybris order code e.g. "LS00123456" */
    @JsonProperty("code")
    private String code;

    /** Internal order status e.g. "COMPLETED", "CANCELLED", "PROCESSING" */
    @JsonProperty("status")
    private String status;

    /** Human-readable status e.g. "Delivered", "Order Placed" */
    @JsonProperty("statusDisplay")
    private String statusDisplay;

    /** ISO timestamp when order was placed e.g. "2024-01-15T10:30:00+0530" */
    @JsonProperty("placed")
    private String placed;

    /** Total order value */
    @JsonProperty("total")
    private OrderPrice total;

    /** Number of line items in this order */
    @JsonProperty("totalItems")
    private Integer totalItems;

    /** Guid used for deep-links */
    @JsonProperty("guid")
    private String guid;
}
