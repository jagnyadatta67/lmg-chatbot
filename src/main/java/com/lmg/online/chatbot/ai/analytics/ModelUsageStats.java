package com.lmg.online.chatbot.ai.analytics;

import lombok.*;

@Data
@Builder
class ModelUsageStats {
    private String model;
    private Long requestCount;
    private Long totalTokens;
    private Double totalCost;
}