package com.lmg.online.chatbot.ai.tools.giftcard.dto;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;

/**
 * DTO representing Gift Card Balance Response (both success and failure)
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GiftCardBalanceResponse {

    @JsonPropertyDescription("Gift card number that was checked")
    private String cardNumber;

    @JsonPropertyDescription("Status of the balance check (e.g., SUCCESS, FAILED)")
    private String status;

    @JsonPropertyDescription("Response message providing details about the balance status")
    private String message;

    @JsonPropertyDescription("Available balance amount on the gift card")
    private Double balanceAmount;

    @JsonPropertyDescription("Currency of the balance amount (e.g., INR, USD)")
    private String currency;

    // ✅ Additional fields for error handling
    @JsonPropertyDescription("Indicates whether an error occurred during the request")
    private Boolean errorOccurred;

    @JsonPropertyDescription("List of errors if the balance enquiry failed")
    private List<GiftCardError> errors;
}
