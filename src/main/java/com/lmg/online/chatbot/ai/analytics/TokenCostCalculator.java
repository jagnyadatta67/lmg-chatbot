package com.lmg.online.chatbot.ai.analytics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility component that converts Spring AI {@link Usage} metadata + model name
 * into a {@link TokenUsage} DTO including estimated cost.
 */
@Slf4j
@Component
public class TokenCostCalculator {

    // Pricing per 1 million tokens (input / output) — keep in sync with AiAnalyticsService
    private static final Map<String, double[]> MODEL_PRICING;
    private static final double[] DEFAULT_PRICING = {0.0, 0.0};

    static {
        Map<String, double[]> m = new HashMap<>();
        m.put("gpt-4o-mini",   new double[]{0.150,  0.600});
        m.put("gpt-4o",        new double[]{2.50,  10.00});
        m.put("gpt-3.5-turbo", new double[]{0.50,   1.50});
        m.put("gpt-4-turbo",   new double[]{10.00, 30.00});
        m.put("gpt-4.1",       new double[]{2.50,   8.00});
        MODEL_PRICING = Collections.unmodifiableMap(m);
    }

    /**
     * Build a {@link TokenUsage} from raw Spring AI {@link Usage} and model name.
     *
     * @param usage Spring AI usage metadata (may be {@code null})
     * @param model Model name returned by the API (may be {@code null})
     * @return populated TokenUsage (never {@code null})
     */
    public TokenUsage buildTokenUsage(Usage usage, String model) {
        int promptTokens     = (usage != null && usage.getPromptTokens()     != null) ? usage.getPromptTokens().intValue()     : 0;
        int completionTokens = (usage != null && usage.getCompletionTokens() != null) ? usage.getCompletionTokens().intValue() : 0;
        int totalTokens      = promptTokens + completionTokens;

        double[] pricing  = resolvePricing(model);
        double totalCost  = (promptTokens / 1_000_000.0) * pricing[0]
                          + (completionTokens / 1_000_000.0) * pricing[1];

        return TokenUsage.builder()
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .cost(totalCost)
                .model(model)
                .build();
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private double[] resolvePricing(String model) {
        if (model == null || model.isBlank()) return DEFAULT_PRICING;

        // 1) exact match
        double[] p = MODEL_PRICING.get(model);
        if (p != null) return p;

        // 2) strip date-style suffixes: "gpt-4.1-2025-04-14" -> "gpt-4.1"
        for (Map.Entry<String, double[]> e : MODEL_PRICING.entrySet()) {
            if (model.startsWith(e.getKey())) return e.getValue();
        }

        // 3) fallback to major version: "gpt-4.1" -> "gpt-4"
        if (model.startsWith("gpt-")) {
            String withoutPrefix = model.substring(4);
            int dot = withoutPrefix.indexOf('.');
            if (dot > 0) {
                double[] mp = MODEL_PRICING.get("gpt-" + withoutPrefix.substring(0, dot));
                if (mp != null) return mp;
            }
        }

        log.warn("No pricing found for model '{}', cost will be $0", model);
        return DEFAULT_PRICING;
    }
}
