package com.lmg.online.chatbot.ai.project.chatmenu.dto;

import com.lmg.online.chatbot.ai.project.chatmenu.entity.ChatMenu;

import java.util.List;

/**
 * Read-only view of a {@link ChatMenu} with its sub-menus embedded inline.
 *
 * The widget fetches GET /api/chat/menus?concept=X and gets the full
 * menu tree in a single response — no second round-trip needed.
 */
public record ChatMenuResponse(
        Long   id,
        String concept,
        String title,
        String icon,
        int    displayOrder,
        boolean active,
        List<ChatSubMenuResponse> subMenus
) {
    /**
     * Build from entity — includes only ACTIVE sub-menus for widget use.
     */
    public static ChatMenuResponse fromWidget(ChatMenu m) {
        List<ChatSubMenuResponse> subs = m.getSubMenus().stream()
                .filter(s -> s.isActive())
                .map(ChatSubMenuResponse::from)
                .toList();
        return new ChatMenuResponse(
                m.getId(), m.getConcept(), m.getTitle(), m.getIcon(),
                m.getDisplayOrder(), m.isActive(), subs
        );
    }

    /**
     * Build from entity — includes ALL sub-menus (active + inactive) for admin.
     */
    public static ChatMenuResponse fromAdmin(ChatMenu m) {
        List<ChatSubMenuResponse> subs = m.getSubMenus().stream()
                .map(ChatSubMenuResponse::from)
                .toList();
        return new ChatMenuResponse(
                m.getId(), m.getConcept(), m.getTitle(), m.getIcon(),
                m.getDisplayOrder(), m.isActive(), subs
        );
    }
}
