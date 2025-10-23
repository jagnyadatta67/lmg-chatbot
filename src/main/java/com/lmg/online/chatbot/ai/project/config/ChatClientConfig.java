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

    @Bean
    public ChatClient.Builder chatClientBuilder(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

 @Bean(name = "generalClient")
    @Primary
    public ChatClient basic(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean(name = "orderTrackClient")
    public ChatClient orderTrackClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean(name = "customerProfile")
    public ChatClient customerProfileClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean(name = "storeLocator")
    public ChatClient storeLocatorClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean(name = "giftCardClient")
    public ChatClient giftCardClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean(name = "policyClient")
    public ChatClient policyClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean(name = "generalQueryClient")
    public ChatClient generalQueryClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean(name = "intentClassifierClient")
    public ChatClient intentClassifierClient(ChatClient.Builder builder) {
        return builder.build();
    }


}