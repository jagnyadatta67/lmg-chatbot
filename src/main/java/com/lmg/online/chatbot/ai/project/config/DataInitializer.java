package com.lmg.online.chatbot.ai.project.config;

import com.lmg.online.chatbot.ai.project.client.ChatbotClientRepository;
import com.lmg.online.chatbot.ai.project.client.ClientAuthService;
import com.lmg.online.chatbot.ai.project.client.ClientAuthService.ClientCreationResult;
import com.lmg.online.chatbot.ai.tools.support.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Runs on every application startup to:
 *  1. Seed a default API client if none exists in the DB.
 *  2. Log the health / row-count of the support_tickets table.
 *
 * The generated API key (step 1) is printed ONCE at WARN level so it's
 * visible even with INFO-level logging. Copy it immediately — it cannot
 * be recovered from the database.
 *
 * On subsequent startups (when clients already exist) step 1 is a no-op.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ChatbotClientRepository clientRepository;
    private final ClientAuthService       clientAuthService;
    private final SupportTicketRepository supportTicketRepository;

    private static final String SEPARATOR =
            "══════════════════════════════════════════════════════════════════";

    @Override
    public void run(String... args) {
        seedDefaultApiClient();
        logSupportTicketTableStatus();
    }

    // ── Step 1: Seed default API client ──────────────────────────────────────

    private void seedDefaultApiClient() {
        long existingClients = clientRepository.count();

        if (existingClients > 0) {
            log.info("DataInitializer: {} client(s) already exist — skipping seed.", existingClients);
            return;
        }

        log.info("DataInitializer: no API clients found — seeding default client...");

        ClientCreationResult result = clientAuthService.createClient(
                "lmg-default",                     // clientId
                "Landmark Group Default Client",   // clientName
                "lmg",                             // concept / brand
                300                                // rateLimitRpm
        );

        // Print the one-time raw key at WARN level so it stands out.
        log.warn(SEPARATOR);
        log.warn("  *** DEFAULT API CLIENT CREATED — COPY THIS KEY NOW ***");
        log.warn("  Client ID  : {}", result.client().getClientId());
        log.warn("  Client Name: {}", result.client().getClientName());
        log.warn("  API Key    : {}", result.rawApiKey());
        log.warn("  This key will NOT be shown again.");
        log.warn("  Use POST /api/admin/clients/{id}/rotate-key to issue a new key.");
        log.warn(SEPARATOR);
    }

    // ── Step 2: Log support_tickets table health ──────────────────────────────

    private void logSupportTicketTableStatus() {
        try {
            long total = supportTicketRepository.count();
            long open  = supportTicketRepository.countOpenTickets();

            log.info(SEPARATOR);
            log.info("  📋 support_tickets table — READY");
            log.info("     Total tickets : {}", total);
            log.info("     Open tickets  : {}", open);
            log.info(SEPARATOR);
        } catch (Exception e) {
            // Table might not exist yet on very first startup before ddl-auto creates it
            log.warn("DataInitializer: Could not query support_tickets table — " +
                     "it may not exist yet or migration is pending. Error: {}", e.getMessage());
        }
    }
}
