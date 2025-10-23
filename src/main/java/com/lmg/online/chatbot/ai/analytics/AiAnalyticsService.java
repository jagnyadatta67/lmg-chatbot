package com.lmg.online.chatbot.ai.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Analytics service — token usage tracking and simple summaries.
 *
 * Improvements:
 * - Resolves pricing for model variants (e.g. "gpt-4.1-2025-04-14" -> "gpt-4.1")
 * - Null-safe token handling
 * - Uses double for average calculations to avoid integer division issues
 * - Robust mapping of repository results (handles Number types)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalyticsService
{
    private final AiUsageAnalyticsRepository repository;

    // Use a modifiable map so you can add entries in future
    private static final Map<String, ModelPricing> MODEL_PRICING;
    private static final ModelPricing DEFAULT_PRICING = new ModelPricing(0.0, 0.0);

    static {
        Map<String, ModelPricing> m = new HashMap<>();
        m.put("gpt-4o-mini", new ModelPricing(0.150, 0.600));
        m.put("gpt-4o", new ModelPricing(2.50, 10.00));
        m.put("gpt-3.5-turbo", new ModelPricing(0.50, 1.50));
        m.put("gpt-4-turbo", new ModelPricing(10.00, 30.00));
        // Add a mapping for gpt-4.1 base name (example prices - replace with real)
        m.put("gpt-4.1", new ModelPricing(2.50, 8.00));
        // you can also add more keys that your infra uses, e.g. "gpt-4.1-2025-04-14" if you want exact match
        MODEL_PRICING = Collections.unmodifiableMap(m);
    }

    private static class ModelPricing {
        final double inputPer1M;
        final double outputPer1M;

        ModelPricing(double inputPer1M, double outputPer1M) {
            this.inputPer1M = inputPer1M;
            this.outputPer1M = outputPer1M;
        }
    }

    /**
     * Resolve pricing for variants such as "gpt-4.1-2025-04-14".
     * - exact match
     * - strip suffix after first '-'
     * - startsWith known key
     * - fallback gpt-major (e.g. gpt-4)
     */
    private ModelPricing resolvePricingForModel(String model) {
        if (model == null || model.isBlank()) return DEFAULT_PRICING;

        // 1) exact match
        ModelPricing p = MODEL_PRICING.get(model);
        if (p != null) return p;

        // 2) strip off suffixes after first '-' (common pattern: "gpt-4.1-2025-04-14" -> "gpt-4.1")
        int dash = model.indexOf('-');
        if (dash > 0) {
            String base = model.substring(0, dash);
            p = MODEL_PRICING.get(base);
            if (p != null) return p;
        }

        // 3) try "startsWith" matching with known keys (useful for keys like "gpt-4" matching "gpt-4.1")
        for (Map.Entry<String, ModelPricing> e : MODEL_PRICING.entrySet()) {
            if (model.startsWith(e.getKey())) return e.getValue();
        }

        // 4) fallback: try to reduce precision (e.g., "gpt-4.1" -> "gpt-4")
        if (model.startsWith("gpt-")) {
            String withoutPrefix = model.substring(4); // after "gpt-"
            int dot = withoutPrefix.indexOf('.');
            if (dot > 0) {
                String major = withoutPrefix.substring(0, dot); // "4"
                String key = "gpt-" + major;
                p = MODEL_PRICING.get(key);
                if (p != null) return p;
            }
        }

        // 5) last resort: default pricing (0)
        log.warn("Using default pricing for unknown model '{}'", model);
        return DEFAULT_PRICING;
    }

    /**
     * Calculate and save token usage analytics
     */
    public AiUsageAnalytics trackUsage(
            String sessionId,
            String userId,
            String userPrompt,
            String aiResponse,
            Integer promptTokens,
            Integer completionTokens,
            String model,
            String finishReason,
            Boolean toolCalled,
            String toolName,
            Long responseTimeMs) {

        // null-safe token values
        int pTokens = (promptTokens == null) ? 0 : promptTokens;
        int cTokens = (completionTokens == null) ? 0 : completionTokens;
        int totalTokens = pTokens + cTokens;

        // Resolve pricing using the helper — handles variants like "gpt-4.1-2025-04-14"
        ModelPricing pricing = resolvePricingForModel(model);

        // compute costs (tokens per 1_000_000) - ensure double division
        double promptCost = (pTokens / 1_000_000.0) * pricing.inputPer1M;
        double completionCost = (cTokens / 1_000_000.0) * pricing.outputPer1M;
        double totalCost = promptCost + completionCost;

        AiUsageAnalytics analytics = AiUsageAnalytics.builder()
                .sessionId(sessionId)
                .userId(userId)
                .userPrompt(userPrompt)
                .promptTokens(pTokens)
                .completionTokens(cTokens)
                .totalTokens(totalTokens)
                .promptCost(promptCost)
                .completionCost(completionCost)
                .totalCost(totalCost)
                .model(model)
                .finishReason(finishReason)
                .toolCalled(toolCalled)
                .toolName(toolName)
                .responseLength(aiResponse != null ? aiResponse.length() : 0)
                .responseTimeMs(responseTimeMs)
                .build();

        AiUsageAnalytics saved = repository.save(analytics);

        log.info("AI Usage tracked - Tokens: {}, Cost: ${}, Model: {}",
                totalTokens, String.format("%.6f", totalCost), model);

        return saved;
    }

    /**
     * Get analytics summary for a time period
     */
    public AnalyticsSummary getSummary(LocalDateTime since) {
        Long totalRequests = repository.getTotalRequestsSince(since);
        Long totalTokens = repository.getTotalTokensSince(since);
        Double totalCost = repository.getTotalCostSince(since);

        long reqs = totalRequests != null ? totalRequests : 0L;
        long toks = totalTokens != null ? totalTokens : 0L;
        double cost = totalCost != null ? totalCost : 0.0;

        double avgTokensPerRequest = reqs > 0 ? (double) toks / reqs : 0.0;
        double avgCostPerRequest = reqs > 0 ? cost / reqs : 0.0;

        return AnalyticsSummary.builder()
                .totalRequests(reqs)
                .totalTokens(toks)
                .totalCost(cost)
                .avgTokensPerRequest(avgTokensPerRequest)
                .avgCostPerRequest(avgCostPerRequest)
                .periodStart(since)
                .periodEnd(LocalDateTime.now())
                .build();
    }

    /**
     * Get usage by model
     */
    public List<ModelUsageStats> getUsageByModel(LocalDateTime since) {
        List<Object[]> results = repository.getUsageByModel(since);

        return results.stream()
                .map(row -> {
                    String modelName = row[0] != null ? row[0].toString() : null;
                    long requestCount = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                    long totalTokensModel = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                    double totalCostModel = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;

                    return ModelUsageStats.builder()
                            .model(modelName)
                            .requestCount(requestCount)
                            .totalTokens(totalTokensModel)
                            .totalCost(totalCostModel)
                            .build();
                })
                .toList();
    }

    /**
     * Get user-specific analytics
     */
    public List<AiUsageAnalytics> getUserAnalytics(String userId) {
        return repository.findByUserId(userId);
    }
}
