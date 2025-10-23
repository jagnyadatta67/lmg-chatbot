package com.lmg.online.chatbot.ai.tools.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponse{
        @JsonProperty(required = true, value = "chat_message")
        private String chat_message;
        @JsonProperty(required = true, value = "customerName")
        private String customerName;
        @JsonProperty(required = true, value = "mobileNo")
        private String mobileNo;
        @JsonProperty(required = true, value = "orderDetailsList")
        private List<OrderDetail> orderDetailsList;
}