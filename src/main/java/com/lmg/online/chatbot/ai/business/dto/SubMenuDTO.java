package com.lmg.online.chatbot.ai.business.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class SubMenuDTO {
    private Long id;
    private String title;
    private String type;
    private int displayOrder;
}