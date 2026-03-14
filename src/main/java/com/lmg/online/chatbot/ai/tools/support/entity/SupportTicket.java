package com.lmg.online.chatbot.ai.tools.support.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persisted record of every support ticket submitted via the Write-to-Us form.
 *
 * Table: support_tickets
 */
@Entity
@Table(name = "support_tickets", indexes = {
        @Index(name = "idx_st_ticket_id",  columnList = "ticketId"),
        @Index(name = "idx_st_user_id",    columnList = "userId"),
        @Index(name = "idx_st_concept",    columnList = "concept"),
        @Index(name = "idx_st_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable ticket reference — e.g. TKT-1715000000000 */
    @Column(nullable = false, unique = true, length = 40)
    private String ticketId;

    // ── Customer-provided fields ──────────────────────────────────────────────

    @Column(length = 100)
    private String name;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    /**
     * Issue category:
     * Query | Return | Delivery | Late Delivery | Cancellation | Refund | Other
     */
    @Column(length = 50)
    private String category;

    /** Customer's description of the issue (up to 2 000 chars) */
    @Column(length = 2000)
    private String message;

    // ── Context fields (auto-populated by widget) ─────────────────────────────

    /** Brand — LIFESTYLE / MAX / HOMECENTRE / BABYSHOP */
    @Column(length = 30)
    private String concept;

    /** Logged-in user ID; NULL for guests */
    @Column(length = 100)
    private String userId;

    /** App platform — ANDROID / IPHONE / Desktop / Mobile */
    @Column(length = 20)
    private String appid;

    /** Deployment environment — uat1 / uat5 / prod */
    @Column(length = 10)
    private String env;

    // ── Internal tracking fields ──────────────────────────────────────────────

    /**
     * Lifecycle status of the ticket.
     * Stored as a String for portability; valid values: OPEN, IN_PROGRESS, CLOSED
     */
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    /** Whether the notification email was dispatched successfully */
    @Builder.Default
    @Column(nullable = false)
    private Boolean emailSent = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Set when status changes to CLOSED */
    private LocalDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null)  status    = "OPEN";
        if (emailSent == null) emailSent = false;
    }
}
