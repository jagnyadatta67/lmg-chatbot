package com.lmg.online.chatbot.ai.tools.storelocator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Address {

    @JsonProperty("town")
    private String city;

    @JsonProperty("formattedAddress")
    private String address;

    @JsonProperty("phone")
    private String contactNumber;

    @JsonProperty("postalCode")
    private String postalCode;

    @JsonPropertyDescription(" store line1")
    @JsonProperty(required = true)
    private  String line1;
    @JsonPropertyDescription(" store line2")
    @JsonProperty(required = true)
    private String line2;

}

