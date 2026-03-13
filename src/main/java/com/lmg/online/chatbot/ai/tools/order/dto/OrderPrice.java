package com.lmg.online.chatbot.ai.tools.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Price object returned by Hybris Commerce v2 order APIs.
 * e.g. { "value": 1999.0, "currencyIso": "INR", "formattedValue": "₹1,999.00" }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderPrice {

    @JsonProperty("value")
    private Double value;

    @JsonProperty("currencyIso")
    private String currencyIso;

    /** Ready-to-display string e.g. "₹1,999.00" */
    @JsonProperty("formattedValue")
    private String formattedValue;
}
