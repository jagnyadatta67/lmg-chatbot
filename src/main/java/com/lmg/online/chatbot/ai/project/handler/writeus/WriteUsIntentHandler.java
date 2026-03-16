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
            ".*\\b(" +
            // Direct contact / ticket requests (explicit action words — safe)
            "write\\s*to\\s*us|contact\\s*support|raise\\s*(a\\s*)?ticket|" +
            "lodge\\s*(a\\s*)?complaint|file\\s*(a\\s*)?complaint|" +
            "submit\\s*(a\\s*)?complaint|register\\s*(a\\s*)?complaint|" +
            // Human agent requests (unambiguous — user wants a human)
            "speak\\s*to\\s*(an?\\s*)?agent|talk\\s*to\\s*(a\\s*)?human|" +
            "talk\\s*to\\s*(an?\\s*)?agent|connect\\s*(me\\s*)?(to\\s*)?(an?\\s*)?agent|" +
            "need\\s*help\\s*from\\s*(a\\s*)?person|real\\s*person|live\\s*agent|" +
            "human\\s*support|speak\\s*to\\s*(a\\s*)?person|" +
            // Escalation signals (explicit escalation — not policy curiosity)
            "escalate|escalation|this\\s*is\\s*(not\\s*)?acceptable|" +
            // Emotional frustration phrases (requires emotion word — avoids generic "not working")
            "i\\s*(am|'m)\\s*(very\\s*)?(angry|frustrated|upset|annoyed|disappointed|furious)|" +
            "so\\s*(angry|frustrated|upset|annoyed|fed\\s*up)|" +
            "extremely\\s*(frustrated|unhappy|disappointed|angry)|" +
            "this\\s*is\\s*(ridiculous|pathetic|terrible|awful|horrible|disgusting)|" +
            "worst\\s*(service|experience|support|app|website)\\s*(ever|i've|i have)?|" +
            "very\\s*unhappy|not\\s*happy\\s*at\\s*all|fed\\s*up|sick\\s*and\\s*tired|" +
            // Unresolved issue phrases (requires "still" / "days" context — not standalone)
            "still\\s*(not\\s*)?(resolved|fixed|delivered|returned|refunded|credited)|" +
            "no\\s*response\\s*(from\\s*(support|team|you))|" +
            "no\\s*one\\s*(is\\s*)?(helping|helped|responded|responding)|" +
            "keep\\s*(waiting|getting\\s*ignored)|waited\\s*(so\\s*)?long|" +
            "been\\s*waiting\\s*(for\\s*)?(days|weeks|hours)|" +
            // Money-specific frustration (requires personal "my" context)
            "where\\s*is\\s*my\\s*(refund|money|cashback)|" +
            "my\\s*money\\s*(not\\s*)?(credited|received|refunded|returned)|" +
            "give\\s*(me\\s*)?my\\s*(money|refund)\\s*back|want\\s*(a\\s*)?refund\\s*now|" +
            // Serious trust violations (high-signal words — never policy)
            "fraud|cheated|scam|mislead(ing)?|false\\s*(promise|information|advertisement)|" +
            // Formal complaint nouns (safe standalone — not policy language)
            "complaint|grievance" +
            ")\\b.*",
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
