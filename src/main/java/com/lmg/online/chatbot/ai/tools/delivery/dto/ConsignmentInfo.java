package com.lmg.online.chatbot.ai.tools.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsignmentInfo {

    @JsonProperty("consignmentCode")
    private String consignmentCode;

    @JsonProperty("consignmentStatus")
    private String consignmentStatus;

    @JsonProperty("shippedDate")
    private String shippedDate;

    @JsonProperty("actualDeliveryDate")
    private String actualDeliveryDate;

    @JsonProperty("quantityShipped")
    private Integer quantityShipped;

    @JsonProperty("deliveryTatBreached")
    private boolean deliveryTatBreached;

    @JsonProperty("dispatchDelayed")
    private boolean dispatchDelayed;

    @JsonProperty("returnWindowExpired")
    private boolean returnWindowExpired;
}
