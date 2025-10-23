package com.lmg.online.chatbot.ai.tools.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class StoreData {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Unique store identifier")
    private String storeId;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Name of the store")
    private String storeName;

    @JsonProperty(required = true)
    @JsonPropertyDescription("City where store is located")
    private String city;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Complete address of the store")
    private String address;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Contact phone number")
    private String contactNumber;
}