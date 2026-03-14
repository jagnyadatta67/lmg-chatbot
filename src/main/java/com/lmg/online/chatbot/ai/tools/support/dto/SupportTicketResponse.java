package com.lmg.online.chatbot.ai.tools.support.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response returned after a support ticket is submitted.
 */
@Data
@Builder
public class SupportTicketResponse {

    /** true = email sent successfully, false = failed */
    private boolean success;

    /** Human-readable message shown in the widget */
    private String message;

    /** Generated ticket reference ID e.g. TKT-1715000000000 */
    private String ticketId;
}
