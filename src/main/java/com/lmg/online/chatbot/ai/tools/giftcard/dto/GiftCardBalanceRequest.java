package com.lmg.online.chatbot.ai.tools.giftcard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GiftCardBalanceRequest {

    @JsonProperty(required = true)
    @JsonPropertyDescription("Gift card number to check the balance")
    private String cardNumber;

    @JsonProperty(required = true)
    @JsonPropertyDescription("PIN or security code associated with the gift card")
    private String pin;
}
