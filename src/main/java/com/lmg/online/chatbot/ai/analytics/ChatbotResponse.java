package com.lmg.online.chatbot.ai.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ChatbotResponse<T> {
    private T data;
    private TokenUsage tokenUsage;
    private Long responseTimeMs;
    private String intent;
    private List<String> links;
    private Map<String,Object> metadata;
    private String errorResponse;
    private boolean success;
 }