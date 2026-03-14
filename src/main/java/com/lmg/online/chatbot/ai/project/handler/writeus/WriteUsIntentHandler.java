package com.lmg.online.chatbot.ai.project.handler.writeus;

import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Handles WRITE_US intent.
 *
 * Triggered when the customer explicitly wants to contact support,
 * raise a ticket, or write a complaint.
 *
 * This handler returns intent="WRITE_US" — the widget renders
 * the Write-to-Us form on receiving this intent.
 * The actual ticket submission goes to POST /api/support/ticket.
 *
 * Also used as fallback by PolicyIntentHandler when RAG returns 0 docs.
 */
@Slf4j
@Component
public class WriteUsIntentHandler implements IntentHandler<String> {

    private static final Pattern WRITE_US_PATTERN = Pattern.compile(
            ".*\\b(write\\s*to\\s*us|contact\\s*support|raise\\s*(a\\s*)?ticket|" +
            "lodge\\s*(a\\s*)?complaint|send\\s*(a\\s*)?message|" +
            "speak\\s*to\\s*(an?\\s*)?agent|talk\\s*to\\s*(a\\s*)?human|" +
            "need\\s*help\\s*from\\s*(a\\s*)?person|escalate|complaint)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public ChatbotResponse<String> handle(ChatRequest request, long startTime) {
        log.info("✉️ Handling WRITE_US intent");
        return ChatbotResponse.<String>builder()
                .data("I'll help you reach our support team. Please fill in the form below and we'll get back to you within 24 hours. 😊")
                .intent(getIntentType())
                .responseTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    @Override
    public String getIntentType() {
        return "WRITE_US";
    }

    @Override
    public boolean canHandle(String query) {
        return WRITE_US_PATTERN.matcher(query).matches();
    }
}
