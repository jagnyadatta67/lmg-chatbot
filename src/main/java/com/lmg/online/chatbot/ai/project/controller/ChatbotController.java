package com.lmg.online.chatbot.ai.project.controller;

import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;

import com.lmg.online.chatbot.ai.project.intent.ChatbotService;
import com.lmg.online.chatbot.ai.project.intent.IntentRouterService;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for chatbot interactions
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatbotController {

    private final IntentRouterService intentRouterService;
    private final ChatbotService chatbotService;

    /**
     * Main endpoint for handling chat requests
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatbotResponse<?>> handleChat(@RequestBody ChatRequest request) {
        String query="";
        if (StringUtils.isNotEmpty(request.getPreviousResponse())){
            query+=request.getPreviousResponse();
        }
        query+=request.getMessage();
        log.info("📨 Received chat request from user: {} {}", request.getUserId(),request.getAppid());
        ChatbotResponse<?> response = chatbotService.handleUserQuery(request);
        log.info("✅ Sent response with intent: {}, time: {}ms",
                response.getIntent(), response.getResponseTimeMs());

        return ResponseEntity.ok(response);
    }




    @PostMapping("/chat/ask")
    public ChatbotResponse<?> chatGet(@RequestBody ChatRequest request) {
        String query="";
        if (StringUtils.isNotEmpty(request.getPreviousResponse())){
            query+=request.getPreviousResponse();
        }
        query+=request.getQuestion();
        request.setMessage(query);
        log.info("📥 Received chat request (GET): {} {}", request.getQuestion(),request.getAppid());
        return  chatbotService.handleUserQuery(request);
    }



    @PostMapping("/chat/nearby-stores")
    public ChatbotResponse<?> getStore(@RequestBody ChatRequest request) {
        request.setMessage("get me the mear by store or mall");
        log.info("📥 Received chat request: {}");
        return chatbotService.handleUserQuery(request);
    }


    /**
     * Health check endpoint showing registered intents
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        List<String> registeredIntents = intentRouterService.getRegisteredIntents();

        return ResponseEntity.ok(new HealthResponse(
                "UP",
                registeredIntents.size(),
                registeredIntents
        ));
    }

    /**
     * Health response DTO
     */
    public record HealthResponse(
            String status,
            int handlerCount,
            List<String> registeredIntents
    ) {}
}