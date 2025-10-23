package com.lmg.online.chatbot.ai.project.intent;

import com.lmg.online.chatbot.ai.deprecated.controller.intent.IntentClassification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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

    private static final String CLASSIFICATION_PROMPT_TEMPLATE = """
        Classify the user's intent from the following query.
        
        Query: %s
        
        Available intents:
        - ORDER_TRACKING: Questions about orders, delivery, shipment status
        - STORE_LOCATOR: Finding store locations, addresses, nearest stores
        - POLICY_QUESTION: Return, exchange, refund, shipping policies
        - CUSTOMER_PROFILE: User profile, account details, personal information
        - GIFT_CARD_BALANCE: Gift card balance inquiry
        - GENERAL_QUERY: General questions, greetings, other queries
        
        Return the intent classification in the following format:
        %s
        """;

    /**
     * Classifies user query using AI when pattern matching fails
     */
    public String classify(String query) {
        log.info("🤖 Using AI classifier for query: {}", query);

        try {
            String prompt = String.format(
                    CLASSIFICATION_PROMPT_TEMPLATE,
                    query,
                    intentOutputConverter.getFormat()
            );

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            IntentClassification classification = intentOutputConverter.convert(response);

            log.info("✅ Classified as: {}", classification.intent());
            return classification.intent().toUpperCase();

        } catch (Exception e) {
            log.error("❌ Error classifying intent, defaulting to GENERAL_QUERY", e);
            return "GENERAL_QUERY";
        }
    }
}