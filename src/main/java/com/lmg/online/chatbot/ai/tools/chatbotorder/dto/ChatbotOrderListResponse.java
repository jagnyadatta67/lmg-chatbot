package com.lmg.online.chatbot.ai.tools.chatbotorder.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level response from the chatbot order listing endpoint:
 *   GET /landmarkshopscommercews/v2/{siteId}/chatbot/user/{customerId}/order
 *
 * Accepts both the chatbot-simplified key ("orders") and the standard
 * Hybris order-list key ("orderDataList") via @JsonAlias.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatbotOrderListResponse {

    @JsonProperty("customerId")
    private String customerId;

    /**
     * Chatbot endpoint returns "orders"; standard Hybris order-list API
     * returns "orderDataList".  Both are accepted here.
     */
    @JsonAlias("orderDataList")
    @JsonProperty("orders")
    private List<ChatbotOrder> orders;

    /** Set by tool on error/empty — AI surfaces this message to the user */
    @JsonProperty("chat_message")
    private String chatMessage;
}
