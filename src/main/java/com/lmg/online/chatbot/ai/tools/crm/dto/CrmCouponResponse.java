package com.lmg.online.chatbot.ai.tools.crm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrmCouponResponse {

    @JsonProperty("result")
    private boolean result;

    @JsonProperty("resultCode")
    private String resultCode;

    @JsonProperty("message")
    private String message;

    @JsonProperty("mobileNumber")
    private String mobileNumber;

    @JsonProperty("couponList")
    private List<CrmCoupon> couponList;

    /** Set by the tool on error — surfaced to the user via the AI response */
    @JsonProperty("chat_message")
    private String chatMessage;
}
