package com.lmg.online.chatbot.ai.tools.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveryItemDetail {

    @JsonProperty("position")
    private Integer position;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("status")
    private String status;

    @JsonProperty("consignment")
    private ConsignmentInfo consignment;

    @JsonProperty("returnDetail")
    private DeliveryReturnDetail returnDetail;
}
