package com.lmg.online.chatbot.ai.tools.support.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload submitted by the widget Write-to-Us form.
 */
@Data
@NoArgsConstructor
public class SupportTicketRequest {

    /** Customer's display name */
    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    /** Contact email */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    @Size(max = 150)
    private String email;

    /** Optional phone number */
    @Size(max = 20)
    private String phone;

    /**
     * Issue category — one of:
     * Query | Return | Delivery | Late Delivery | Cancellation | Refund | Other
     */
    @NotBlank(message = "Category is required")
    private String category;

    /** Customer's description of the issue */
    @NotBlank(message = "Message is required")
    @Size(max = 2000)
    private String message;

    // ── Context fields (populated automatically by the widget) ──────────────

    /** Brand — LIFESTYLE / MAX / HOMECENTRE / BABYSHOP */
    private String concept;

    /** Logged-in user ID (null for guests) */
    private String userId;

    /** App identifier — ANDROID / IPHONE / Desktop / Mobile */
    private String appid;

    /** Environment — uat1 / uat5 / prod */
    private String env;
}
