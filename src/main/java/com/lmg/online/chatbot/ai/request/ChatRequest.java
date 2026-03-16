package com.lmg.online.chatbot.ai.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    private  String accessToken;

    /**
     * The user's chat message. Required.
     * Capped at 2000 characters to prevent oversized prompt injection.
     */
    @NotBlank(message = "message must not be blank")
    @Size(max = 2000, message = "message must not exceed 2000 characters")
    private String message;

    /** Previous AI response included for conversation context. */
    @Size(max = 4000, message = "previousResponse must not exceed 4000 characters")
    private String previousResponse;

    /** Caller's user identifier. */
    private String userId;

    /** Alternative question field used by the /chat/ask endpoint. */
    @Size(max = 2000, message = "question must not exceed 2000 characters")
    private String question;

    /** Brand / concept (e.g. "lmg", "max", "splash"). */
    private String concept;

    /** Runtime environment hint passed by the client. */
    private String env;

    /** Latitude for store-locator queries. */
    private double latitude;

    /** Longitude for store-locator queries. */
    private double longitude;

    /** Application identifier. */
    private String appid;

    /** Gift card number (only used for gift-card-balance intent). */
    private String cardNumber;

    /** Gift card PIN (only used for gift-card-balance intent). */
    private String pin;

    /**
     * Order number for single-order tracking.
     * Populated by the widget when the user provides an explicit order number.
     * The handler also extracts it from {@code message} via regex as a fallback.
     */
    private String orderNo;

    /**
     * Optional intent pin set by the frontend to bypass AI classification entirely.
     * When present and non-blank, IntentRouterService routes directly to this intent's
     * handler without running pattern matching or the AI classifier.
     *
     * Use this when the frontend already knows the intent (e.g. user entered an order
     * number in a dedicated input field after ORDER_TRACKING prompted for it).
     *
     * Values must match an exact intent string registered in IntentRouterService,
     * e.g. "ORDER_TRACKING", "DELIVERY_TRACKING", "RETURN_STATUS".
     */
    private String intentHint;
}
