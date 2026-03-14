package com.lmg.online.chatbot.ai.project.handler.general;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handler for general queries that don't fit other intents.
 *
 * Prompt strategy:
 *  - System message  → persona + brand rules (sent once as "system" role, not repeated in user text)
 *  - User   message  → just the customer query + optional previous-context (lean, low tokens)
 *  - Local shortcut  → pure greetings are answered locally, zero API call
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeneralQueryIntentHandler implements IntentHandler<String> {

    /** Simple greeting patterns resolved locally — no API call needed */
    private static final java.util.regex.Pattern GREETING_PATTERN =
            java.util.regex.Pattern.compile(
                    "^(hi+|hello+|hey+|good\\s+(morning|afternoon|evening|day)|howdy|sup|greetings)[!.,?\\s]*$",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );

    private static final List<String> GREETING_REPLIES = List.of(
            "Hi there! 👋 How can I help you today?",
            "Hello! 😊 What can I assist you with today?",
            "Hey! Great to see you. How can I help?",
            "Good to have you here! How may I assist you today?",
            "Hi! I'm here to help. What do you need today?"
    );

    private static final java.util.Random RANDOM = new java.util.Random();

    @Autowired
    @Qualifier("generalClient")
    private ChatClient chatClient;

    private final AiAnalyticsService aiAnalyticsService;

    @Override
    public ChatbotResponse<String> handle(ChatRequest request, long startTime) {
        log.info("💬 Handling GENERAL_QUERY intent");

        String message = request.getMessage() == null ? "" : request.getMessage().trim();

        // ── Fast path: local greeting — zero API call ──────────────────────────
        if (GREETING_PATTERN.matcher(message).matches()) {
            String reply = GREETING_REPLIES.get(RANDOM.nextInt(GREETING_REPLIES.size()));
            log.info("👋 Local greeting shortcut used — no API call");
            return buildResponse(reply, request, startTime);
        }

        // ── AI path: system + user message ────────────────────────────────────
        String systemPrompt = buildSystemPrompt(request);
        String userMessage  = buildUserMessage(message, request);

        String responseText = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

        return buildResponse(responseText, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "GENERAL_QUERY";
    }

    @Override
    public boolean canHandle(String query) {
        return false; // fallback handler — always routed here last
    }

    // ── Prompt builders ────────────────────────────────────────────────────────

    /**
     * System prompt — defines persona and strict response rules.
     * Key rule: every non-greeting reply MUST end with the customer support number.
     */
    private String buildSystemPrompt(ChatRequest request) {
        String concept = request.getConcept() != null ? request.getConcept().toUpperCase() : "Landmark";
        String phone   = ConceptBaseUrlResolver.getRawPhoneNumber(concept);

        return String.format("""
                You are a customer support assistant for %s (part of Landmark Group, India).
                Be warm, professional, and concise.

                Follow these rules strictly:

                RULE 1 — GREETING (hi, hello, good morning, hey, etc.):
                Reply warmly and ask how you can help. Do NOT include any phone number.
                Example: "Hello! 😊 How can I help you today?"

                RULE 2 — HELP / SUPPORT QUESTION (orders, returns, delivery, exchange, stores, products, policies, gift cards):
                Answer clearly in 2–3 sentences.
                ALWAYS end your reply with exactly this line:
                "For further assistance, please call our customer support at %s."

                RULE 3 — UNCLEAR / GIBBERISH / CANNOT UNDERSTAND:
                Reply: "I'm sorry, I couldn't understand your query. For immediate help, please call our customer support at %s."
                Never try to guess or interpret meaningless input.

                RULE 4 — OFF-TOPIC (anything unrelated to %s or shopping):
                Politely say this is outside your scope and end with:
                "For further assistance, please call our customer support at %s."

                IMPORTANT:
                - Never fabricate order IDs, store names, prices, or policy details.
                - Keep replies under 60 words.
                - For Rules 2, 3, and 4 — the customer support number is MANDATORY at the end. No exceptions.
                """,
                concept, phone, phone, concept, phone);
    }

    /**
     * User message — lean: just the query + optional previous context.
     */
    private String buildUserMessage(String message, ChatRequest request) {
        StringBuilder sb = new StringBuilder();

        if (request.getPreviousResponse() != null && !request.getPreviousResponse().isBlank()) {
            sb.append("Previous assistant reply: ")
              .append(request.getPreviousResponse().trim())
              .append("\n\n");
        }

        sb.append("Customer: ").append(message);
        return sb.toString();
    }

    // ── Response builders ──────────────────────────────────────────────────────

    private ChatbotResponse<String> buildResponse(String responseText, ChatRequest request, long startTime) {
        long responseTime = System.currentTimeMillis() - startTime;
        trackAnalytics(request, responseText, responseTime);
        return ChatbotResponse.<String>builder()
                .data(responseText)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }

    private void trackAnalytics(ChatRequest request, String responseText, long responseTime) {
        aiAnalyticsService.trackUsage(
                null, null,
                request.getMessage(),
                responseText,
                0, 0,
                "gpt-4o-mini",
                "stop",
                false,
                "none",
                responseTime
        );
        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);
    }
}