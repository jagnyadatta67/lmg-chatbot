package com.lmg.online.chatbot.ai.project.intent;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.deprecated.controller.intent.IntentClassification;
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
        
        Available intents:
        - ORDER_TRACKING: Questions about live orders, delivery, shipment status. 
          Do not classify as ORDER_TRACKING for policy-related queries. 
          Instead, use POLICY_QUESTION for return, exchange, or refund-related questions.
        - STORE_LOCATOR: Finding store locations, addresses, nearest stores.
        - POLICY_QUESTION: Return, exchange, refund, shipping policies.
        - CUSTOMER_PROFILE: User profile, account details, personal information.
        - GIFT_CARD_BALANCE: Gift card balance inquiry.
        - GENERAL_QUERY: Greetings, general or other uncategorized questions.
       
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
