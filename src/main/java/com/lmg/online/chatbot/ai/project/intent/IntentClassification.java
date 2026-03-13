package com.lmg.online.chatbot.ai.project.intent;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured AI output for intent classification.
 * Used by {@link IntentClassifier} to parse the JSON response from OpenAI.
 */
public record IntentClassification(
        @JsonProperty("intent") String intent,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("requires_order_data") boolean requiresOrderData,
        @JsonProperty("extracted_info") ExtractedInfo extractedInfo
) {
    public record ExtractedInfo(
            @JsonProperty("mobile_number") String mobileNumber,
            @JsonProperty("order_number") String orderNumber
    ) {}
}
