package com.lmg.online.chatbot.ai.tools.chatbotorder.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Single order object inside the chatbot order list response.
 *
 * Field aliases handle both:
 *  - chatbot-simplified endpoint  (orderNo, orderStatus, orderDate, orderAmount, entries[])
 *  - standard Hybris order API    (code, status/statusDisplay, created, totalPrice.value, entries[])
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatbotOrder {

    /** "orderNo" from chatbot endpoint; "code" from standard Hybris API */
    @JsonAlias("code")
    @JsonProperty("orderNo")
    private String orderNo;

    /**
     * "orderStatus" from chatbot endpoint;
     * "statusDisplay" (human-readable e.g. "Placed") from standard Hybris API.
     * "status" (machine code e.g. "SUBMITTED_TO_WAREHOUSE") used as fallback.
     */
    @JsonAlias({"status", "statusDisplay"})
    @JsonProperty("orderStatus")
    private String orderStatus;

    /** "orderDate" from chatbot endpoint; "created" from standard Hybris API */
    @JsonAlias("created")
    @JsonProperty("orderDate")
    private String orderDate;

    /** Direct numeric amount from chatbot endpoint */
    @JsonProperty("orderAmount")
    private Double orderAmount;

    @JsonProperty("numberOfShipments")
    private Integer numberOfShipments;

    @JsonProperty("numberOfProductsDelivered")
    private Integer numberOfProductsDelivered;

    @JsonProperty("entries")
    private List<ChatbotOrderEntry> entries;

    /**
     * Handles the standard Hybris "totalPrice": { "value": 419.0 } structure.
     * Sets orderAmount only when the chatbot endpoint didn't already provide it.
     */
    @JsonSetter("totalPrice")
    public void setOrderAmountFromTotalPrice(Map<String, Object> totalPriceMap) {
        if (this.orderAmount == null && totalPriceMap != null
                && totalPriceMap.get("value") instanceof Number) {
            this.orderAmount = ((Number) totalPriceMap.get("value")).doubleValue();
        }
    }
}
