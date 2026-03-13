package com.lmg.online.chatbot.ai.project.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Core service for API-key lifecycle management.
 *
 * Security model:
 *   - Raw API keys are generated once, shown once, and never persisted.
 *   - Only the SHA-256 hex digest is stored in the DB.
 *   - On every incoming request the provided key is hashed and compared against stored hashes.
 *   - Key rotation generates a new random key; the old hash is immediately replaced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientAuthService {

    private final ChatbotClientRepository clientRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ----------------------------------------------------------------
    // Authentication
    // ----------------------------------------------------------------

    /**
     * Validates an incoming raw API key against the database.
     * On success, updates {@code lastUsedAt} so admins can see active clients.
     *
     * @param rawApiKey the plain-text key from the HTTP header
     * @return the active {@link ChatbotClient} or empty if key is unknown / client is inactive
     */
    @Transactional
    public Optional<ChatbotClient> validateApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }

        String hash = sha256Hex(rawApiKey);
        Optional<ChatbotClient> clientOpt = clientRepository.findByApiKeyHashAndActiveTrue(hash);

        clientOpt.ifPresent(client -> {
            // Touch lastUsedAt — the entity is managed within this transaction so
            // Hibernate will flush the update automatically.
            client.setLastUsedAt(Instant.now());
        });

        return clientOpt;
    }

    // ----------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------

    /**
     * Creates a new API client and returns a {@link ClientCreationResult} that
     * carries both the persisted entity and the <em>raw</em> API key.
     *
     * <strong>The raw key is returned ONCE and is never retrievable again.</strong>
     *
     * @param clientId     unique short identifier, e.g. "lmg-web"
     * @param clientName   human-readable label
     * @param concept      brand / tenant, e.g. "lmg"
     * @param rateLimitRpm per-minute request cap for this client
     * @return result carrying the saved entity + the one-time plain-text key
     * @throws IllegalArgumentException if {@code clientId} is already in use
     */
    @Transactional
    public ClientCreationResult createClient(String clientId,
                                             String clientName,
                                             String concept,
                                             int rateLimitRpm) {
        if (clientRepository.existsByClientId(clientId)) {
            throw new IllegalArgumentException(
                    "A client with ID '" + clientId + "' already exists.");
        }

        String rawKey = generateApiKey();
        String hash   = sha256Hex(rawKey);

        ChatbotClient client = ChatbotClient.builder()
                .clientId(clientId)
                .clientName(clientName)
                .concept(concept)
                .apiKeyHash(hash)
                .active(true)
                .rateLimitRpm(rateLimitRpm)
                .build();

        clientRepository.save(client);
        log.info("Created new chatbot client: id='{}', name='{}'", clientId, clientName);

        return new ClientCreationResult(client, rawKey);
    }

    /**
     * Rotates the API key for an existing client.
     * The old key becomes invalid immediately.
     *
     * @param clientId the target client's short identifier
     * @return the new plain-text API key — show once and discard
     * @throws IllegalArgumentException if no client with that ID exists
     */
    @Transactional
    public String rotateApiKey(String clientId) {
        ChatbotClient client = findOrThrow(clientId);

        String rawKey = generateApiKey();
        client.setApiKeyHash(sha256Hex(rawKey));

        log.info("Rotated API key for client: '{}'", clientId);
        return rawKey;
    }

    /**
     * Deactivates a client (soft-delete).
     * The client's row is kept in the DB; it simply cannot authenticate.
     *
     * @throws IllegalArgumentException if no client with that ID exists
     */
    @Transactional
    public void deactivateClient(String clientId) {
        ChatbotClient client = findOrThrow(clientId);
        client.setActive(false);
        log.info("Deactivated client: '{}'", clientId);
    }

    /**
     * Re-enables a previously deactivated client.
     *
     * @throws IllegalArgumentException if no client with that ID exists
     */
    @Transactional
    public void activateClient(String clientId) {
        ChatbotClient client = findOrThrow(clientId);
        client.setActive(true);
        log.info("Activated client: '{}'", clientId);
    }

    /**
     * Returns all clients (active and inactive) for admin listing.
     */
    @Transactional(readOnly = true)
    public List<ChatbotClient> listAllClients() {
        return clientRepository.findAll();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /**
     * Computes the SHA-256 hex digest of a UTF-8 string.
     * Public so it can be used in tests without instantiating the full service.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java specification; this branch is unreachable.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generates a cryptographically random API key.
     * Format: {@code lmg-<64 hex chars>} (prefix makes keys instantly recognisable).
     */
    private static String generateApiKey() {
        byte[] bytes = new byte[32];   // 256 bits of entropy
        SECURE_RANDOM.nextBytes(bytes);
        return "lmg-" + HexFormat.of().formatHex(bytes);
    }

    private ChatbotClient findOrThrow(String clientId) {
        return clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No client found with ID: '" + clientId + "'"));
    }

    // ----------------------------------------------------------------
    // Value object returned from createClient / rotateApiKey
    // ----------------------------------------------------------------

    /**
     * Carries both the persisted entity and the one-time raw API key.
     * The caller is responsible for showing {@code rawApiKey} to the admin
     * user immediately — it is never stored and cannot be recovered.
     */
    public record ClientCreationResult(ChatbotClient client, String rawApiKey) {}
}
