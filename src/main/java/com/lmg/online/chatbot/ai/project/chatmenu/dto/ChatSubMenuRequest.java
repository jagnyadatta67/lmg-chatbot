package com.lmg.online.chatbot.ai.project.chatmenu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a sub-menu item.
 */
public record ChatSubMenuRequest(

        @NotBlank(message = "title is required")
        @Size(max = 150, message = "title must not exceed 150 characters")
        String title,

        @Size(max = 10, message = "icon must not exceed 10 characters")
        String icon,

        /**
         * Open-string intent key — the widget dispatches on this.
         * Leave blank or null to show a free-text input fallback.
         * Examples: "TRACK_ORDER", "GIFT_CARD_BALANCE", "NEARBY_STORE"
         */
        @Size(max = 100, message = "intentKey must not exceed 100 characters")
        String intentKey,

        int displayOrder,

        boolean active
) {}
