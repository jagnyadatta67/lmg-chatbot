package com.lmg.online.chatbot.ai.business.config;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusineessConfig {
    @Bean
    public TokenTextSplitter textSplitter() {
        return new TokenTextSplitter();
    }
}
