package com.lmg.online.chatbot.ai.tools.storelocator.dto;



import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoreLocatorAPIResponse {

    @JsonProperty("pointOfServicess")
    private List<StoreData> stores;


}










