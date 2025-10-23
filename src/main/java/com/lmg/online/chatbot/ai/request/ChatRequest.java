package com.lmg.online.chatbot.ai.request;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String previousResponse;
    private String userId;
    private String question;
    private String concept;
    private String env;
    private double latitude;
    private double longitude;
    private String appid;
    private String cardNumber;
    private String pin;



}
