package com.lmg.online.chatbot.ai.tools.returnstatus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from the chatbot return status endpoint:
 *   GET /landmarkshopscommercews/v2/{siteId}/chatbot/user/{customerId}/return
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReturnStatusResponse {

    @JsonProperty("type")
    private String type;

    @JsonProperty("consignmentCode")
    private String consignmentCode;

    @JsonProperty("returnId")
    private String returnId;

    @JsonProperty("rma")
    private String rma;

    @JsonProperty("returnStatus")
    private String returnStatus;

    @JsonProperty("returnCreationDate")
    private String returnCreationDate;

    @JsonProperty("returnInitiated")
    private boolean returnInitiated;

    @JsonProperty("returnPickupDelayed")
    private boolean returnPickupDelayed;

    @JsonProperty("totalRefundAmount")
    private Double totalRefundAmount;

    @JsonProperty("faqLink")
    private String faqLink;

    /** Set by tool on error — AI surfaces this message */
    @JsonProperty("chat_message")
    private String chatMessage;
}
