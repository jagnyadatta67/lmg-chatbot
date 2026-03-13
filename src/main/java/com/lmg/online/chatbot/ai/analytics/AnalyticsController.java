package com.lmg.online.chatbot.ai.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Admin REST API for AI usage analytics.
 *
 * All endpoints live under /api/admin/analytics — no API key required
 * (protected the same way as other /api/admin/** paths).
 *
 * Endpoints:
 *   GET /api/admin/analytics/summary?hours=24   — headline KPIs
 *   GET /api/admin/analytics/models?hours=24    — per-model breakdown
 *   GET /api/admin/analytics/recent?hours=24    — recent query log
 *   GET /api/admin/analytics/tools?hours=24     — tool-call breakdown
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Admin: AI token usage and cost analytics")
public class AnalyticsController {

    private final AiAnalyticsService analyticsService;
    private final AiUsageAnalyticsRepository repository;

    // ──────────────────────────────────────────────────────────────────────
    // Summary KPIs
    // ──────────────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @Operation(summary = "Headline KPIs — total requests, tokens, cost, avg response time")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(defaultValue = "24") int hours) {

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        AnalyticsSummary summary = analyticsService.getSummary(since);
        Double avgResponseMs   = repository.getAvgResponseTimeSince(since);

        return ResponseEntity.ok(Map.of(
                "periodHours",         hours,
                "periodStart",         summary.getPeriodStart().toString(),
                "periodEnd",           summary.getPeriodEnd().toString(),
                "totalRequests",       summary.getTotalRequests(),
                "totalTokens",         summary.getTotalTokens(),
                "totalCostUsd",        round(summary.getTotalCost()),
                "avgTokensPerRequest", round(summary.getAvgTokensPerRequest()),
                "avgCostPerRequest",   round6(summary.getAvgCostPerRequest()),
                "avgResponseTimeMs",   avgResponseMs != null ? Math.round(avgResponseMs) : 0
        ));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Per-model breakdown
    // ──────────────────────────────────────────────────────────────────────

    @GetMapping("/models")
    @Operation(summary = "Token and cost breakdown per AI model")
    public ResponseEntity<List<ModelUsageStats>> getModelStats(
            @RequestParam(defaultValue = "24") int hours) {

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<ModelUsageStats> stats = analyticsService.getUsageByModel(since);
        return ResponseEntity.ok(stats);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Recent query log
    // ──────────────────────────────────────────────────────────────────────

    @GetMapping("/recent")
    @Operation(summary = "Recent AI queries — shows prompt, tokens, cost, response time")
    public ResponseEntity<List<QueryRow>> getRecentQueries(
            @RequestParam(defaultValue = "24") int hours) {

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<AiUsageAnalytics> rows = repository.findByCreatedAtAfterOrderByCreatedAtDesc(since);

        List<QueryRow> view = rows.stream()
                .limit(200)
                .map(QueryRow::from)
                .toList();

        return ResponseEntity.ok(view);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tool-call breakdown
    // ──────────────────────────────────────────────────────────────────────

    @GetMapping("/tools")
    @Operation(summary = "Breakdown of tool calls made by the AI")
    public ResponseEntity<List<Map<String, Object>>> getToolStats(
            @RequestParam(defaultValue = "24") int hours) {

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<Object[]> raw  = repository.getToolUsageSince(since);

        List<Map<String, Object>> result = raw.stream()
                .map(row -> Map.<String, Object>of(
                        "toolName",  row[0] != null ? row[0].toString() : "unknown",
                        "callCount", row[1] != null ? ((Number) row[1]).longValue() : 0L
                ))
                .toList();

        return ResponseEntity.ok(result);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Inner DTO — safe projection of AiUsageAnalytics for the dashboard
    // ──────────────────────────────────────────────────────────────────────

    public record QueryRow(
            Long   id,
            String createdAt,
            String userId,
            String prompt,
            String model,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Double  totalCostUsd,
            Long    responseTimeMs,
            Boolean toolCalled,
            String  toolName
    ) {
        static QueryRow from(AiUsageAnalytics a) {
            // Truncate long prompts for the table display
            String prompt = a.getUserPrompt();
            if (prompt != null && prompt.length() > 120) {
                prompt = prompt.substring(0, 120) + "…";
            }
            return new QueryRow(
                    a.getId(),
                    a.getCreatedAt() != null ? a.getCreatedAt().toString() : null,
                    a.getUserId(),
                    prompt,
                    a.getModel(),
                    a.getPromptTokens(),
                    a.getCompletionTokens(),
                    a.getTotalTokens(),
                    a.getTotalCost(),
                    a.getResponseTimeMs(),
                    a.getToolCalled(),
                    a.getToolName()
            );
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round6(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
