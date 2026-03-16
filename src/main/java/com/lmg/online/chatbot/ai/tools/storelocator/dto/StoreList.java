package com.lmg.online.chatbot.ai.tools.storelocator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoreList {

    @JsonProperty(required = true)
    @JsonPropertyDescription("List of stores matching the query")
    private List<StoreView> stores;

    /** Set by tool on error — surfaced to the user as a friendly message */
    @JsonProperty("chat_message")
    private String chatMessage;
}
