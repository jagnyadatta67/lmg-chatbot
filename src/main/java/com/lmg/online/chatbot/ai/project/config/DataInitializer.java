package com.lmg.online.chatbot.ai.project.config;

import com.lmg.online.chatbot.ai.project.client.ChatbotClientRepository;
import com.lmg.online.chatbot.ai.project.client.ClientAuthService;
import com.lmg.online.chatbot.ai.project.client.ClientAuthService.ClientCreationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a default API client on first startup if no clients exist in the DB.
 *
 * The generated API key is printed ONCE to the console (log level WARN so it
 * stands out even with INFO-level logging).  Copy it immediately — it cannot
 * be recovered from the database.
 *
 * On subsequent startups (when clients already exist) this class is a no-op.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ChatbotClientRepository clientRepository;
    private final ClientAuthService       clientAuthService;

    private static final String SEPARATOR =
            "══════════════════════════════════════════════════════════════════";

    @Override
    public void run(String... args) {
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

        // Print the one-time raw key to the log at WARN level so it's visible
        // even when the root log level is INFO.
        log.warn(SEPARATOR);
        log.warn("  *** DEFAULT API CLIENT CREATED — COPY THIS KEY NOW ***");
        log.warn("  Client ID  : {}", result.client().getClientId());
        log.warn("  Client Name: {}", result.client().getClientName());
        log.warn("  API Key    : {}", result.rawApiKey());
        log.warn("  This key will NOT be shown again.");
        log.warn("  Use POST /api/admin/clients/{id}/rotate-key to issue a new key.");
        log.warn(SEPARATOR);
    }
}
