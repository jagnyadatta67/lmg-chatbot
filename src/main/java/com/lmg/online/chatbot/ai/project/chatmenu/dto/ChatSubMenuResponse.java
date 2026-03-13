package com.lmg.online.chatbot.ai.project.chatmenu.dto;

import com.lmg.online.chatbot.ai.project.chatmenu.entity.ChatSubMenu;

/**
 * Read-only view of a {@link ChatSubMenu} sent to the widget and admin.
 */
public record ChatSubMenuResponse(
        Long   id,
        String title,
        String icon,
        String intentKey,
        int    displayOrder,
        boolean active
) {
    public static ChatSubMenuResponse from(ChatSubMenu s) {
        return new ChatSubMenuResponse(
                s.getId(),
                s.getTitle(),
                s.getIcon(),
                s.getIntentKey(),
                s.getDisplayOrder(),
                s.isActive()
        );
    }
}
