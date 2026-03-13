package com.lmg.online.chatbot.ai.project.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link ChatbotClient}.
 *
 * All sensitive lookups use the SHA-256 hash of the API key —
 * the raw key is never persisted or queried.
 */
public interface ChatbotClientRepository extends JpaRepository<ChatbotClient, Long> {

    /**
     * Used by {@link ClientAuthService} to authenticate an incoming request.
     * Only returns clients that are currently active.
     *
     * @param apiKeyHash SHA-256 hex of the raw key provided by the caller
     * @return the matching active client, or empty if key is invalid / client is deactivated
     */
    Optional<ChatbotClient> findByApiKeyHashAndActiveTrue(String apiKeyHash);

    /**
     * Looks up a client by its short identifier (e.g. "lmg-web").
     * Returns both active and inactive clients so admin operations work on all.
     */
    Optional<ChatbotClient> findByClientId(String clientId);

    /** Returns all active clients (for admin listing). */
    List<ChatbotClient> findAllByActiveTrue();

    /** Quick existence check used during client creation to prevent duplicates. */
    boolean existsByClientId(String clientId);

    /**
     * Bulk-updates {@code lastUsedAt} directly in the DB (bypasses Hibernate dirty-check
     * for high-throughput scenarios where full entity load is not needed).
     */
    @Modifying
    @Query("UPDATE ChatbotClient c SET c.lastUsedAt = :ts WHERE c.apiKeyHash = :hash")
    void updateLastUsedAt(@Param("hash") String apiKeyHash, @Param("ts") Instant ts);
}
