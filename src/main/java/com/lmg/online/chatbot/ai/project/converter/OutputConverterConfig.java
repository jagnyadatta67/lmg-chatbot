package com.lmg.online.chatbot.ai.project.converter;

import com.lmg.online.chatbot.ai.project.intent.IntentClassification;
import com.lmg.online.chatbot.ai.tools.delivery.dto.DeliveryResponse;
import com.lmg.online.chatbot.ai.tools.returnstatus.dto.ReturnStatusResponse;
import com.lmg.online.chatbot.ai.tools.storelocator.dto.StoreList;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for output converters used by AI-assisted intent handlers.
 *
 * A BeanOutputConverter is required for every handler that uses AI to parse
 * a structured response (i.e. handlers that call chatClient.prompt().call()).
 * Direct-mapping handlers (OrderTrackingIntentHandler, ChatbotOrderIntentHandler,
 * WalletIntentHandler, CustomerProfileIntentHandler, GiftCardBalanceIntentHandler)
 * do NOT need a converter — they map DTO fields directly via tool calls.
 */
@Configuration
public class OutputConverterConfig {

    @Bean
    public BeanOutputConverter<StoreList> storeLocatorConverter() {
        return new BeanOutputConverter<>(StoreList.class);
    }

    @Bean
    public BeanOutputConverter<IntentClassification> intentOutputConverter() {
        return new BeanOutputConverter<>(IntentClassification.class);
    }

    // ── New converters (AI-assisted handlers only) ─────────────────────────

    /**
     * Used by DeliveryTrackingIntentHandler — AI extracts orderNo + entryPk
     * from the user message and calls DeliveryTrackingTool.
     */
    @Bean
    public BeanOutputConverter<DeliveryResponse> deliveryOutputConverter() {
        return new BeanOutputConverter<>(DeliveryResponse.class);
    }

    /**
     * Used by ReturnStatusIntentHandler — AI extracts orderNo + rmaNo
     * from the user message and calls ReturnStatusTool.
     */
    @Bean
    public BeanOutputConverter<ReturnStatusResponse> returnOutputConverter() {
        return new BeanOutputConverter<>(ReturnStatusResponse.class);
    }
}