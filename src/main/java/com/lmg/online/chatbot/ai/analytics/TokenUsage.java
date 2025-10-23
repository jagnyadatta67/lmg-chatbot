package com.lmg.online.chatbot.ai.analytics;

import lombok.*;

@Data
@Builder
public class TokenUsage {
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Double cost;
    private String model;
}
