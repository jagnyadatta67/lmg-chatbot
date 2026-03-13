package com.lmg.online.chatbot.ai.project.chatmenu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a top-level chat menu.
 */
public record ChatMenuRequest(

        @NotBlank(message = "concept is required")
        @Size(max = 50, message = "concept must not exceed 50 characters")
        String concept,

        @NotBlank(message = "title is required")
        @Size(max = 150, message = "title must not exceed 150 characters")
        String title,

        @Size(max = 10, message = "icon must not exceed 10 characters")
        String icon,

        int displayOrder,

        boolean active
) {}
