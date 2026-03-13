package com.lmg.online.chatbot.ai.tools.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveryReturnDetail {

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

    @JsonProperty("faqLink")
    private String faqLink;
}
