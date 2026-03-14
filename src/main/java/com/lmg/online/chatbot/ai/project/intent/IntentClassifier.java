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

        === CRITICAL ROUTING RULES ===

        RULE A — NUMBER REQUIRED:
        ORDER_TRACKING, RETURN_STATUS, DELIVERY_TRACKING, and GIFT_CARD_BALANCE
        each require a specific reference number in the query.
        If the number is ABSENT → classify as POLICY_QUESTION instead.

        RULE B — POLICY FALLBACK:
        Any question about orders, returns, delivery, or gift cards WITHOUT a
        reference number is a policy/general question → use POLICY_QUESTION.

        === AVAILABLE INTENTS ===

        - ORDER_TRACKING: Customer asks about a SPECIFIC order using its order number.
          REQUIRES: an order number (7–12 digits) in the query.
          Examples WITH number: "track order 9419396447", "status of 9419396447", "9419396447".
          Examples WITHOUT number → use POLICY_QUESTION: "where is my order", "track my order",
          "what happened to my order", "check my order status".

        - ORDER_LISTING: Customer wants to see their full order history (no order number needed).
          Use this for ANY query asking about multiple orders or order history without a specific number.
          Examples: "orders", "my orders", "show orders", "order list", "order history",
          "my recent orders", "recent purchases", "past orders", "all my orders",
          "show my orders", "what did I buy", "my purchases", "view order history",
          "what have I ordered", "my order history".
          Single word "orders" or "my orders" ALWAYS maps here.
          Do not use if a specific order number (7–12 digits) is present — use ORDER_TRACKING instead.

        - DELIVERY_TRACKING: Questions about delivery/shipment status for a SPECIFIC order.
          REQUIRES: an order number (7–12 digits) in the query.
          Examples WITH number: "delivery status for order 3400746000", "track shipment 3400746000".
          Examples WITHOUT number → use POLICY_QUESTION: "when will my order arrive",
          "where is my delivery", "dispatch status", "out for delivery".

        - RETURN_STATUS: Questions about return/refund status for a SPECIFIC returned item.
          REQUIRES: an order number or RMA number in the query.
          Examples WITH number: "return status for order 9419396447", "check RMA 10221001".
          Examples WITHOUT number → use POLICY_QUESTION: "what is my refund status",
          "return pickup status", "when will I get my money back", "my return status".
          Do not confuse with POLICY_QUESTION which is about the general return policy.

        - WALLET_BALANCE: Customer asks about their wallet, store credit, or cashback balance.
          Examples: "my wallet balance", "how much credit do I have", "store credit balance".

        - STORE_LOCATOR: Finding store locations, addresses, or nearest stores.
          Examples: "nearest store", "Lifestyle store in Mumbai", "mall near me".

        - POLICY_QUESTION: ALL general policy, procedure, or information questions.
          Use this when there is NO specific reference number.
          Use this for: return policy, exchange policy, refund policy, shipping charges,
          cancellation procedure, delivery timelines, how-to questions, gift card policy/usage,
          order/delivery/return questions WITHOUT a reference number.
          Examples: "what is return policy", "how to cancel order", "how long does delivery take",
          "how do I return an item", "what are shipping charges", "how does gift card work",
          "where is my order" (no number), "when will I get refund" (no number).

        - CUSTOMER_PROFILE: User profile, account details, or personal information.

        - GIFT_CARD_BALANCE: Customer explicitly wants to CHECK their gift card balance.
          REQUIRES: card number present OR clear intent to check balance (widget will collect card number).
          Examples: "check my gift card balance", "gift card balance", "how much is on my gift card".
          Do NOT use for policy questions: "how does gift card work", "gift card validity" → use POLICY_QUESTION.

        - WRITE_US: Customer wants to raise a ticket, lodge a complaint, or contact support.
          Examples: "write to you", "raise a ticket", "lodge a complaint", "talk to an agent",
          "speak to a human", "escalate my issue", "contact support team".
          Do NOT use for policy questions or status queries.

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
