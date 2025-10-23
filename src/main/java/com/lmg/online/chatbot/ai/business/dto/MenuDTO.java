package com.lmg.online.chatbot.ai.business.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor@NoArgsConstructor
@Builder
public class MenuDTO {
    private Long id;
    private String title;
    private int displayOrder;
    private List<SubMenuDTO> subMenus;
}