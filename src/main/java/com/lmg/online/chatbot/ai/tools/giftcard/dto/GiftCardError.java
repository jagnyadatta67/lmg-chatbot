package com.lmg.online.chatbot.ai.tools.giftcard.dto;



import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

/**
 * Represents a single error entry in gift card response
 */
@Data
public class GiftCardError {

    @JsonPropertyDescription("Human-readable error message or key")
    private String message;

    @JsonPropertyDescription("Error reason or technical code")
    private String reason;

    @JsonPropertyDescription("Subject related to the error, like card or request type")
    private String subject;

    @JsonPropertyDescription("Type of the subject (e.g., GIFT_CARD_FAILURE)")
    private String subjectType;

    @JsonPropertyDescription("Type of error (e.g., BALANCE_ENQUIRY_FAILURE)")
    private String type;
}
