package com.lmg.online.chatbot.ai.tools.storelocator.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreView {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Unique store identifier")
    private String storeId;
    @JsonProperty(required = true)
    @JsonPropertyDescription("Unique store name")
    private String storeName;
    @JsonProperty(required = true)
    @JsonPropertyDescription(" store city")
    private String city;
    @JsonProperty(required = true)
    @JsonPropertyDescription(" store address")
    private String address;
    @JsonProperty(required = true)
    @JsonPropertyDescription(" store contact number")
    private String contactNumber;
    @JsonProperty(required = true)
    @JsonPropertyDescription(" store workinghours")
    private String workingHours;
    @JsonProperty(required = true)
    @JsonPropertyDescription(" store latitude")
    private double latitude;
    @JsonProperty(required = true)
    @JsonPropertyDescription(" store longitude")
    private double longitude;
    @JsonPropertyDescription(" store longitude")
    @JsonProperty(required = true)
    private double distance;
    @JsonPropertyDescription(" store line1")
    @JsonProperty(required = true)
    private  String line1;
    @JsonPropertyDescription(" store line2")
    @JsonProperty(required = true)
    private String line2;
    @JsonProperty(required = true)
    @JsonPropertyDescription(" store postalCode")
    private String postalCode;
}