package com.lmg.online.chatbot.ai.tools.wallet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from the chatbot wallet endpoint:
 *   GET /landmarkshopscommercews/v2/{siteId}/chatbot/user/{customerId}/wallet
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletResponse {

    @JsonProperty("walletAmount")
    private Double walletAmount;

    /** Relative URL to the wallet details page e.g. "/my-credit/walletDetails" */
    @JsonProperty("walletLink")
    private String walletLink;

    /** Set by tool on error — AI surfaces this to the user */
    @JsonProperty("chat_message")
    private String chatMessage;
}
