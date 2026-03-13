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
     * System prompt — defines persona and rules ONCE per call (not stuffed into user text).
     * Sent as the OpenAI "system" role → cheaper + cleaner separation of concerns.
     */
    private String buildSystemPrompt(ChatRequest request) {
        String concept    = request.getConcept() != null ? request.getConcept() : "Landmark";
        String phone      = ConceptBaseUrlResolver.getPhoneNumber(concept);
        String contactLine = (phone != null && !phone.isBlank())
                ? "For further help the customer can call " + phone + "."
                : "";

        return String.format("""
                You are a friendly and knowledgeable customer support assistant for %s, \
                a leading retail brand in India (part of Landmark Group).

                Personality & tone:
                - Warm, professional, and concise.
                - Vary your phrasing naturally — never give the exact same opening twice.
                - Use emojis sparingly (1 per message max).

                Behaviour rules:
                1. ON-TOPIC (orders, delivery, returns, stores, policies, gift cards, products for %s):
                   Answer clearly in 2–4 sentences. Be helpful and specific.
                2. OFF-TOPIC (anything unrelated to %s or retail/shopping):
                   Politely redirect: "I specialise in %s shopping support. %s \
                   Is there something about %s I can help you with?"
                3. UNKNOWN / VAGUE: Ask a short clarifying question.
                4. NEVER fabricate order IDs, store details, prices, or policy specifics.
                5. Keep all replies under 80 words unless the customer explicitly asks for more detail.
                """,
                concept, concept, concept, concept, contactLine, concept);
    }

    /**
     * User message — lean payload: just the customer query + optional previous context.
     * Keeping this short reduces prompt tokens on every call.
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