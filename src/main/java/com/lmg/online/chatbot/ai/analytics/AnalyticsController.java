package com.lmg.online.chatbot.ai.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AiAnalyticsService analyticsService;

    @GetMapping("/summary/today")
    public AnalyticsSummary getTodaySummary() {
        return analyticsService.getSummary(LocalDateTime.now().withHour(0).withMinute(0));
    }

    @GetMapping("/summary/week")
    public AnalyticsSummary getWeeklySummary() {
        return analyticsService.getSummary(LocalDateTime.now().minusDays(7));
    }

    @GetMapping("/summary/month")
    public AnalyticsSummary getMonthlySummary() {
        return analyticsService.getSummary(LocalDateTime.now().minusDays(30));
    }

    @GetMapping("/models/week")
    public List<ModelUsageStats> getModelUsageWeekly() {
        return analyticsService.getUsageByModel(LocalDateTime.now().minusDays(7));
    }

    @GetMapping("/user/{userId}")
    public List<AiUsageAnalytics> getUserAnalytics(@PathVariable String userId) {
        return analyticsService.getUserAnalytics(userId);
    }

    @GetMapping("/summary/custom")
    public AnalyticsSummary getCustomSummary(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDateTime start = LocalDateTime.parse(startDate);
        return analyticsService.getSummary(start);
    }
}