package com.lmg.online.chatbot.ai.deprecated.controller;

import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;

import com.lmg.online.chatbot.ai.deprecated.controller.intent.SmartChatService;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;


@Slf4j
public class SmartChatController {
    private final SmartChatService smartChatService;

    public SmartChatController(SmartChatService smartChatService) {
        this.smartChatService = smartChatService;
    }

  @PostMapping("/chat")
    public ChatbotResponse<?> chat(@RequestBody ChatRequest request) {
        String query="";
        if (StringUtils.isNotEmpty(request.getPreviousResponse())){
            query+=request.getPreviousResponse();
        }
        query+=request.getMessage();


              log.info("📥 Received chat request: {}",query);
      System.out.println(request.getAppid() + " "+request.getMessage());
        return smartChatService.handleUserQuery(request);
    }

    @PostMapping("/chat/ask")
    public ChatbotResponse<?> chatGet(@RequestBody ChatRequest request) {
        String query="";
        if (StringUtils.isNotEmpty(request.getPreviousResponse())){
            query+=request.getPreviousResponse();
        }
        query+=request.getQuestion();
        request.setMessage(query);
        log.info("📥 Received chat request (GET): {}", request.getQuestion());
        return smartChatService.handleUserQuery( request);
    }



    @PostMapping("/chat/nearby-stores")
    public ChatbotResponse<?> getStore(@RequestBody ChatRequest request) {
       request.setMessage("get me the mear by store or mall");
       log.info("📥 Received chat request: {}");
        return smartChatService.handleUserQuery(request);
    }
    record QuestionRequest(String question,String previousResponse) {}
    record AnswerResponse(String chat_message) {}
}
