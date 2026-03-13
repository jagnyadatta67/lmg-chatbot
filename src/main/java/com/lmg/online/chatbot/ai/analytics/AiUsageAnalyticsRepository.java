package com.lmg.online.chatbot.ai.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiUsageAnalyticsRepository extends JpaRepository<AiUsageAnalytics, Long> {

    List<AiUsageAnalytics> findByUserId(String userId);

    List<AiUsageAnalytics> findBySessionId(String sessionId);

    List<AiUsageAnalytics> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT SUM(a.totalTokens) FROM AiUsageAnalytics a WHERE a.createdAt >= :start")
    Long getTotalTokensSince(LocalDateTime start);

    @Query("SELECT SUM(a.totalCost) FROM AiUsageAnalytics a WHERE a.createdAt >= :start")
    Double getTotalCostSince(LocalDateTime start);

    @Query("SELECT COUNT(a) FROM AiUsageAnalytics a WHERE a.createdAt >= :start")
    Long getTotalRequestsSince(LocalDateTime start);

    @Query("SELECT a.model, COUNT(a), SUM(a.totalTokens), SUM(a.totalCost) " +
            "FROM AiUsageAnalytics a WHERE a.createdAt >= :start GROUP BY a.model")
    List<Object[]> getUsageByModel(LocalDateTime start);

    /** Recent N rows for the live-query table on the dashboard. */
    List<AiUsageAnalytics> findTop50ByOrderByCreatedAtDesc();

    /** Recent rows within a time window, newest first. */
    List<AiUsageAnalytics> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    /** Avg response time since a point in time. */
    @Query("SELECT AVG(a.responseTimeMs) FROM AiUsageAnalytics a WHERE a.createdAt >= :start")
    Double getAvgResponseTimeSince(LocalDateTime start);

    /** Tool-call breakdown since a point in time. */
    @Query("SELECT a.toolName, COUNT(a) FROM AiUsageAnalytics a " +
           "WHERE a.createdAt >= :start AND a.toolCalled = true GROUP BY a.toolName")
    List<Object[]> getToolUsageSince(LocalDateTime start);
}