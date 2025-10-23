package com.lmg.online.chatbot.ai.tools.storelocator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoreData {

    @JsonProperty("name")
    private String storeId;

    @JsonProperty("displayName")
    private String storeName;

    @JsonProperty("workingHours")
    private String workingHours;

    @JsonProperty("address")
    private Address address;

    @JsonProperty("geoPoint")
    private GeoPoint geoPoint;
    @JsonProperty("line1")
    private  String line1;
    @JsonProperty("line2")
    private String line2;
    @JsonProperty("postalCode")
    private String postalCode;
}