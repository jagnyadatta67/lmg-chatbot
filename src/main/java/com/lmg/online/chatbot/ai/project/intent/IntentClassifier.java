package com.lmg.online.chatbot.ai.project.intent;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

/**
 * AI-based intent classifier - only used when pattern matching fails
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassifier {

    private final ChatClient chatClient;
    private final BeanOutputConverter<IntentClassification> intentOutputConverter;
    private final AiAnalyticsService aiAnalyticsService;

    private static final String CLASSIFICATION_PROMPT_TEMPLATE = """
        Classify the user's intent from the following query.

        Query: %s

        === KEY DISTINCTION: PERSONAL STATUS vs POLICY QUESTION ===
        - Personal status query  ("where is MY order", "MY return status", "when will I get MY refund")
          → route to the relevant action intent (ORDER_TRACKING, RETURN_STATUS, etc.)
          → the backend will ask the user for an order number if one is missing.
        - General policy question ("what is the return policy", "how do I cancel", "how long does delivery take")
          → route to POLICY_QUESTION.

        === AVAILABLE INTENTS ===

        - ORDER_TRACKING: Customer asking about status of their own specific order.
          Use when the user says "my order", "track order", "order status", "where is my order",
          "check my order", or provides an order number.
          Examples: "track order 9419396447", "where is my order", "what is my order status",
          "has my order shipped", "my order status".
          Do NOT use for: "what is the return policy", "how do I cancel" → use POLICY_QUESTION.

        - ORDER_LISTING: Customer wants to see their full order history.
          Use for ANY query about multiple orders or order history.
          Examples: "my orders", "order history", "show my orders", "past orders",
          "recent purchases", "what have I ordered", "all my orders".
          Do not use if a specific order number is present — use ORDER_TRACKING instead.

        - DELIVERY_TRACKING: Customer asking about delivery/shipment status of their own order.
          Use when the user asks about their own delivery, shipment, dispatch, or when it will arrive.
          Examples: "when will my order arrive", "where is my delivery", "track my shipment",
          "delivery status", "out for delivery", "dispatch status for order 3400746000".
          Do NOT use for: "how long does delivery take" → use POLICY_QUESTION.

        - RETURN_STATUS: Customer asking about return or refund status of their own item.
          Use when the user asks about their own return pickup, refund status, or exchange status.
          Examples: "my return status", "when will I get my refund", "refund status",
          "return pickup status", "my exchange status", "return status for order 9419396447".
          Do NOT use for: "what is the return policy", "how do I initiate a return" → use POLICY_QUESTION.

        - WALLET_BALANCE: Customer asks about their wallet, store credit, or cashback balance.
          Examples: "my wallet balance", "how much credit do I have", "store credit balance".

        - STORE_LOCATOR: Finding store locations, addresses, or nearest stores.
          Examples: "nearest store", "Lifestyle store in Mumbai", "mall near me".

        - POLICY_QUESTION: General policy, procedure, or how-to information questions.
          Use for questions about HOW things work or WHAT the rules are — not personal status.
          Examples: "what is return policy", "how to cancel order", "how long does delivery take",
          "what are shipping charges", "how does gift card work", "can I exchange without receipt",
          "what is the refund timeline", "cancellation policy".
          Do NOT use for personal status queries like "where is MY order" or "MY return status".

        - CUSTOMER_PROFILE: User profile, account details, or personal information.
          Examples: "my profile", "my account", "show my details".

        - GIFT_CARD_BALANCE: Customer wants to check their gift card balance.
          Examples: "check my gift card balance", "gift card balance", "how much is on my gift card".
          Do NOT use for policy questions: "how does gift card work" → use POLICY_QUESTION.

        - WRITE_US: Customer wants to raise a ticket, lodge a complaint, or contact support.
          Examples: "raise a ticket", "lodge a complaint", "talk to an agent",
          "speak to a human", "escalate my issue", "contact support team".

        - GENERAL_QUERY: Greetings or completely unrelated questions.
          Examples: "hi", "hello", "what products do you sell", "do you have sale".

        Return the intent classification in the following JSON format:
        %s
        """;

    /**
     * Classifies user query using AI when pattern matching fails
     */
    public String classify(String query) {
        log.info("🤖 Using AI classifier for query: {}", query);
        long startTime = System.currentTimeMillis();

        try {
            String prompt = String.format(CLASSIFICATION_PROMPT_TEMPLATE, query, intentOutputConverter.getFormat());

            // ✅ Get the full ChatResponse (not just content)
            ChatResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();
            long responseTime = System.currentTimeMillis() - startTime;

            // ✅ Extract structured data safely
            String outputText = response.getResult().getOutput().getText();
            String modelName = response.getMetadata().getModel();
            String finishReason = response.getResult().getMetadata().getFinishReason();

            Usage usage = response.getMetadata().getUsage();
            int promptTokens = usage != null ? usage.getPromptTokens().intValue() : 0;
            int completionTokens = usage != null ? usage.getCompletionTokens().intValue() : 0;

            // ✅ Track analytics
            aiAnalyticsService.trackUsage(
                    null,
                    null,
                    query,
                    outputText,
                    promptTokens,
                    completionTokens,
                    modelName,
                    finishReason,
                    false,
                    "none",
                    responseTime
            );

            // ✅ Convert AI JSON output into IntentClassification bean
            IntentClassification classification = intentOutputConverter.convert(outputText);

            log.info("✅ Classified as: {}", classification.intent());
            return classification.intent().toUpperCase();

        } catch (Exception e) {
            log.error("❌ Error classifying intent, defaulting to GENERAL_QUERY", e);
            return "GENERAL_QUERY";
        }
    }
}
