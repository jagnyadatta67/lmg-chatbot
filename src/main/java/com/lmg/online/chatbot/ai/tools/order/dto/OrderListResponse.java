package com.lmg.online.chatbot.ai.tools.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level response from the Hybris v2 order listing endpoint:
 *   GET /landmarkshopscommercews/v2/{siteId}/en/users/{userId}/v2/orders
 *
 * Also carries an optional chat_message set by the tool on error/fallback,
 * so the AI can surface a friendly message directly.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderListResponse {

    /** List of order summaries returned by the API */
    @JsonProperty("orders")
    private List<OrderSummary> orders;

    /** Pagination metadata */
    @JsonProperty("pagination")
    private OrderListPagination pagination;

    /**
     * Fallback / error message set by the tool (not from API).
     * The AI uses this when the API call fails or the list is empty.
     */
    @JsonProperty("chat_message")
    private String chatMessage;
}
