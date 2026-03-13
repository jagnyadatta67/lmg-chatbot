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
        
        Available intents:
        - ORDER_TRACKING: Customer asks about the status or details of a SPECIFIC order,
          usually with an order number (7–12 digit number) in the message.
          Examples: "track order 9419396447", "where is order 9419396447", "status of 9419396447",
          "check my order 9419396447", "9419396447 delivery status".
          Also classify as ORDER_TRACKING when the message contains ONLY a numeric order number
          (e.g. "9419396447") — the customer is replying to a prompt asking for their order number.
          Do not classify as ORDER_TRACKING for general order listing (no specific order number mentioned) — use ORDER_LISTING instead.
        - ORDER_LISTING: Customer wants to see their order history or list of recent orders.
          Examples: "show my orders", "my recent purchases", "order history", "what did I buy".
          Do not use ORDER_LISTING if a specific order number is mentioned — use ORDER_TRACKING instead.
        - DELIVERY_TRACKING: Questions about delivery status, shipment, when an order will arrive,
          dispatch status, or out-for-delivery status for a specific item.
          Examples: "where is my delivery", "when will my order arrive", "track shipment for order 3400746000".
          Do not classify as RETURN_STATUS — use DELIVERY_TRACKING for pre-delivery/in-transit queries.
        - RETURN_STATUS: Questions about return status, refund status, RMA, return pickup,
          refund amount, or money-back for a returned item.
          Examples: "what is my refund status", "return pickup status", "check RMA 10221001",
          "when will I get my money back". Do not confuse with POLICY_QUESTION which is about return policies.
        - WALLET_BALANCE: Customer asks about their wallet, store credit, cashback, or available credit balance.
          Examples: "my wallet balance", "how much credit do I have", "store credit balance".
        - STORE_LOCATOR: Finding store locations, addresses, nearest stores.
        - POLICY_QUESTION: Return, exchange, refund, shipping policies, shipping fees, price, how to cancel, return policy etc.
          This is about general policies — not about a specific order's return or refund status.
        - CUSTOMER_PROFILE: User profile, account details, personal information.
        - GIFT_CARD_BALANCE: Gift card balance inquiry.
        - GENERAL_QUERY: Greetings, general or other uncategorized questions.
        
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
