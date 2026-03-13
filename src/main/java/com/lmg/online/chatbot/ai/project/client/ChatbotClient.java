package com.lmg.online.chatbot.ai.project.client;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Represents an authorised API consumer (a "client" app or tenant).
 *
 * Only the SHA-256 hash of the raw API key is stored — the plain-text key
 * is shown exactly once at creation time and is never persisted.
 *
 * Table: chatbot_clients
 */
@Entity
@Table(
        name = "chatbot_clients",
        indexes = {
                @Index(name = "idx_clients_client_id",  columnList = "clientId",  unique = true),
                @Index(name = "idx_clients_key_hash",   columnList = "apiKeyHash", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatbotClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique short identifier for this client, e.g. "lmg-web", "lmg-mobile".
     * Used as the authenticated principal name after key validation.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String clientId;

    /** Human-readable display name, e.g. "Landmark Web App". */
    @Column(nullable = false, length = 128)
    private String clientName;

    /**
     * SHA-256 hex digest of the raw API key (64 hex chars).
     * Never store the raw key — hash it before persisting.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String apiKeyHash;

    /**
     * Business concept / brand this client operates under, e.g. "lmg", "max".
     * Forwarded to the chatbot service for context-aware responses.
     */
    @Column(nullable = false, length = 50)
    private String concept;

    /** Whether this client may authenticate. Soft-delete via deactivation. */
    @Column(nullable = false)
    private boolean active = true;

    /** Per-client request rate cap (requests per minute). */
    @Column(nullable = false)
    private int rateLimitRpm = 60;

    /** Set automatically on first persist; never updated. */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Updated on every successful authentication via {@code ClientAuthService}. */
    @Column
    private Instant lastUsedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
