package com.lmg.online.chatbot.ai.project.converter;

import com.lmg.online.chatbot.ai.deprecated.controller.intent.IntentClassification;
import com.lmg.online.chatbot.ai.tools.giftcard.dto.GiftCardBalanceResponse;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderResponse;
import com.lmg.online.chatbot.ai.tools.storelocator.dto.StoreList;
import com.lmg.online.chatbot.ai.tools.user.dto.CustomerProfileResponseDTO;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for output converters used by intent handlers
 */
@Configuration
public class OutputConverterConfig {

    @Bean
    public BeanOutputConverter<OrderResponse> orderOutputConverter() {
        return new BeanOutputConverter<>(OrderResponse.class);
    }

    @Bean
    public BeanOutputConverter<CustomerProfileResponseDTO> customerProfileOutputConverter() {
        return new BeanOutputConverter<>(CustomerProfileResponseDTO.class);
    }

    @Bean
    public BeanOutputConverter<StoreList> storeLocatorConverter() {
        return new BeanOutputConverter<>(StoreList.class);
    }

    @Bean
    public BeanOutputConverter<GiftCardBalanceResponse> giftCardBalanceConverter() {
        return new BeanOutputConverter<>(GiftCardBalanceResponse.class);
    }

    @Bean
    public BeanOutputConverter<IntentClassification> intentOutputConverter() {
        return new BeanOutputConverter<>(IntentClassification.class);
    }
}