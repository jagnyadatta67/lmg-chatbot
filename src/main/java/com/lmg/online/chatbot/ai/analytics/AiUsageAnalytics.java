package com.lmg.online.chatbot.ai.analytics;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_usage_analytics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;
    private String userId;
    private String userPrompt;

    // Token usage
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    // Cost calculations
    private Double promptCost;
    private Double completionCost;
    private Double totalCost;

    // Model info
    private String model;
    private String finishReason;

    // Tool usage
    private Boolean toolCalled;
    private String toolName;

    // Response metadata
    private Integer responseLength;
    private Long responseTimeMs;



    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}