package com.lmg.online.chatbot.ai.analytics;
import org.springframework.stereotype.Component;

@Component
public class TokenCostCalculator
{

    // Pricing per 1M tokens
    private static final double GPT_4O_MINI_INPUT = 0.150;
    private static final double GPT_4O_MINI_OUTPUT = 0.600;

    private static final double GPT_4O_INPUT = 2.50;
    private static final double GPT_4O_OUTPUT = 10.00;

    private static final double GPT_35_TURBO_INPUT = 0.50;
    private static final double GPT_35_TURBO_OUTPUT = 1.50;

    public double calculateCost(String model, int promptTokens, int completionTokens) {
        double inputCost;
        double outputCost;

        switch (model.toLowerCase()) {
            case "gpt-4o-mini":
                inputCost = (promptTokens / 1_000_000.0) * GPT_4O_MINI_INPUT;
                outputCost = (completionTokens / 1_000_000.0) * GPT_4O_MINI_OUTPUT;
                break;
            case "gpt-4o":
                inputCost = (promptTokens / 1_000_000.0) * GPT_4O_INPUT;
                outputCost = (completionTokens / 1_000_000.0) * GPT_4O_OUTPUT;
                break;
            case "gpt-3.5-turbo":
                inputCost = (promptTokens / 1_000_000.0) * GPT_35_TURBO_INPUT;
                outputCost = (completionTokens / 1_000_000.0) * GPT_35_TURBO_OUTPUT;
                break;
            default:
                inputCost = 0.0;
                outputCost = 0.0;
        }

        return inputCost + outputCost;
    }

    public TokenUsage buildTokenUsage(org.springframework.ai.chat.metadata.Usage usage, String model) {
        int promptTokens = usage.getPromptTokens().intValue();
        int completionTokens = usage.getCompletionTokens().intValue();
        int totalTokens = usage.getTotalTokens().intValue();
        double cost = calculateCost(model, promptTokens, completionTokens);

        return TokenUsage.builder()
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .cost(cost)
                .model(model)
                .build();
    }
}