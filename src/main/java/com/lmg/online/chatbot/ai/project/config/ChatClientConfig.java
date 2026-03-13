package com.lmg.online.chatbot.ai.project.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for ChatClient beans used by different intent handlers
 */
@Configuration
public class ChatClientConfig {

    // General Query ChatClient
    @Bean(name = "generalClient")
    @Primary
    public ChatClient generalQueryClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    // Order Tracking Client
    @Bean(name = "orderTrackClient")
    public ChatClient orderTrackClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    // Store Locator Client
    @Bean(name = "storeLocator")
    public ChatClient storeLocatorClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    // Gift Card Client
    @Bean(name = "giftCardClient")
    public ChatClient giftCardClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    // Policy Client
    @Bean(name = "policyClient")
    public ChatClient policyClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    // Intent Classifier Client
    @Bean(name = "intentClassifierClient")
    public ChatClient intentClassifierClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    // Delivery Tracking Client (AI extracts orderNo + entryPk from message)
    @Bean(name = "deliveryClient")
    public ChatClient deliveryClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    // Return Status Client (AI extracts orderNo + rmaNo from message)
    @Bean(name = "returnStatusClient")
    public ChatClient returnStatusClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}