package com.lmg.online.chatbot.ai.analytics;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AnalyticsSummary {
    private Long totalRequests;
    private Long totalTokens;
    private Double totalCost;
    private Double avgTokensPerRequest;
    private Double avgCostPerRequest;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
}
