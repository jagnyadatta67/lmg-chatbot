package com.lmg.online.chatbot.ai.tools.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import java.util.List;

@Data
public class StoreLocatorResponse {

    @JsonProperty(required = true)
    @JsonPropertyDescription("List of stores matching the query")
    private List<StoreData> stores;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Status of the response")
    private String status;
}