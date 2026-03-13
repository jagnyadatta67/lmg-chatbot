package com.lmg.online.chatbot.ai.project.controller;

import com.lmg.online.chatbot.ai.project.client.ChatbotClient;
import com.lmg.online.chatbot.ai.project.client.ClientAuthService;
import com.lmg.online.chatbot.ai.project.client.ClientAuthService.ClientCreationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin REST API for managing chatbot API clients (tenants).
 *
 * All endpoints live under /api/admin/clients.
 * This prefix is excluded from API-key validation in ApiKeyAuthFilter
 * (secured separately — protect these endpoints with network/gateway rules
 * or add a separate admin-secret filter in production).
 *
 * Endpoints:
 *   GET    /api/admin/clients            — list all clients
 *   POST   /api/admin/clients            — create a new client (returns key ONCE)
 *   POST   /api/admin/clients/{id}/rotate-key  — rotate the API key
 *   POST   /api/admin/clients/{id}/deactivate  — soft-delete a client
 *   POST   /api/admin/clients/{id}/activate    — re-enable a client
 */
@RestController
@RequestMapping("/api/admin/clients")
@RequiredArgsConstructor
@Tag(name = "Client Management", description = "Admin: create and manage chatbot API clients")
public class ClientManagementController {

    private final ClientAuthService clientAuthService;

    // ----------------------------------------------------------------
    // List
    // ----------------------------------------------------------------

    @GetMapping
    @Operation(summary = "List all API clients (active and inactive)")
    public ResponseEntity<List<ClientSummary>> listClients() {
        List<ClientSummary> summaries = clientAuthService.listAllClients()
                .stream()
                .map(ClientSummary::from)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    // ----------------------------------------------------------------
    // Create
    // ----------------------------------------------------------------

    @PostMapping
    @Operation(
            summary = "Create a new API client",
            description = "Returns the raw API key **once** in the response body. "
                    + "Store it immediately — it cannot be retrieved again."
    )
    public ResponseEntity<Map<String, Object>> createClient(
            @Valid @RequestBody CreateClientRequest req) {

        ClientCreationResult result = clientAuthService.createClient(
                req.clientId(),
                req.clientName(),
                req.concept(),
                req.rateLimitRpm()
        );

        ChatbotClient client = result.client();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "clientId",     client.getClientId(),
                "clientName",   client.getClientName(),
                "concept",      client.getConcept(),
                "rateLimitRpm", client.getRateLimitRpm(),
                "active",       client.isActive(),
                "createdAt",    client.getCreatedAt().toString(),
                "apiKey",       result.rawApiKey(),
                "warning",      "Save this API key now — it will NOT be shown again."
        ));
    }

    // ----------------------------------------------------------------
    // Rotate Key
    // ----------------------------------------------------------------

    @PostMapping("/{clientId}/rotate-key")
    @Operation(
            summary = "Rotate the API key for a client",
            description = "Immediately invalidates the old key and issues a new one. "
                    + "The new key is returned **once** — store it immediately."
    )
    public ResponseEntity<Map<String, Object>> rotateKey(@PathVariable String clientId) {
        String newKey = clientAuthService.rotateApiKey(clientId);
        return ResponseEntity.ok(Map.of(
                "clientId",  clientId,
                "newApiKey", newKey,
                "rotatedAt", Instant.now().toString(),
                "warning",   "Save this API key now — it will NOT be shown again."
        ));
    }

    // ----------------------------------------------------------------
    // Activate / Deactivate
    // ----------------------------------------------------------------

    @PostMapping("/{clientId}/deactivate")
    @Operation(summary = "Deactivate a client (soft-delete — key is immediately rejected)")
    public ResponseEntity<Map<String, String>> deactivate(@PathVariable String clientId) {
        clientAuthService.deactivateClient(clientId);
        return ResponseEntity.ok(Map.of(
                "status",   "deactivated",
                "clientId", clientId
        ));
    }

    @PostMapping("/{clientId}/activate")
    @Operation(summary = "Re-activate a previously deactivated client")
    public ResponseEntity<Map<String, String>> activate(@PathVariable String clientId) {
        clientAuthService.activateClient(clientId);
        return ResponseEntity.ok(Map.of(
                "status",   "activated",
                "clientId", clientId
        ));
    }

    // ----------------------------------------------------------------
    // Exception handling
    // ----------------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "status",  400,
                "error",   "Bad Request",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // ----------------------------------------------------------------
    // Inner records (request / response DTOs)
    // ----------------------------------------------------------------

    /**
     * Request body for POST /api/admin/clients.
     *
     * @param clientId     unique short ID, e.g. "lmg-web"
     * @param clientName   human-readable name, e.g. "Landmark Web App"
     * @param concept      brand / tenant, e.g. "lmg"
     * @param rateLimitRpm per-minute request cap (≥ 1)
     */
    public record CreateClientRequest(
            @NotBlank @Size(max = 64,  message = "clientId must not exceed 64 characters")
            String clientId,

            @NotBlank @Size(max = 128, message = "clientName must not exceed 128 characters")
            String clientName,

            @NotBlank @Size(max = 50,  message = "concept must not exceed 50 characters")
            String concept,

            @Min(value = 1, message = "rateLimitRpm must be at least 1")
            int rateLimitRpm
    ) {}

    /**
     * Safe read-only view of a {@link ChatbotClient} — never includes the apiKeyHash.
     */
    public record ClientSummary(
            Long   id,
            String clientId,
            String clientName,
            String concept,
            boolean active,
            int    rateLimitRpm,
            String createdAt,
            String lastUsedAt
    ) {
        static ClientSummary from(ChatbotClient c) {
            return new ClientSummary(
                    c.getId(),
                    c.getClientId(),
                    c.getClientName(),
                    c.getConcept(),
                    c.isActive(),
                    c.getRateLimitRpm(),
                    c.getCreatedAt()  != null ? c.getCreatedAt().toString()  : null,
                    c.getLastUsedAt() != null ? c.getLastUsedAt().toString() : null
            );
        }
    }
}
