package com.lmg.online.chatbot.ai.tools.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level response from the chatbot delivery tracking endpoint:
 *   GET /landmarkshopscommercews/v2/{siteId}/chatbot/user/{customerId}/delivery
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveryResponse {

    @JsonProperty("orderNo")
    private String orderNo;

    @JsonProperty("orderEntryPk")
    private String orderEntryPk;

    @JsonProperty("productCode")
    private String productCode;

    @JsonProperty("productImageUrl")
    private String productImageUrl;

    @JsonProperty("productUrl")
    private String productUrl;

    @JsonProperty("exchangeable")
    private boolean exchangeable;

    @JsonProperty("returnable")
    private boolean returnable;

    @JsonProperty("item_details")
    private List<DeliveryItemDetail> itemDetails;

    /** Set by tool on error — AI surfaces this to the user */
    @JsonProperty("chat_message")
    private String chatMessage;
}
