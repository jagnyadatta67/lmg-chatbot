package com.lmg.online.chatbot.ai.project.chatmenu.controller;

import com.lmg.online.chatbot.ai.project.chatmenu.dto.ChatMenuResponse;
import com.lmg.online.chatbot.ai.project.chatmenu.service.ChatMenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public widget API — no extra auth required here because the ApiKeyAuthFilter
 * already validates the X-API-Key header on every /api/** call.
 *
 * Endpoint:
 *   GET /api/chat/menus?concept=HOMECENTRE
 *
 * Returns all active menus for the given concept with their active
 * sub-menus embedded inline (no second round-trip needed by the widget).
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/menus")
@RequiredArgsConstructor
public class ChatMenuWidgetController {

    private final ChatMenuService chatMenuService;

    /**
     * Fetch menus for a concept.
     *
     * @param concept brand concept, e.g. "HOMECENTRE", "MAX" (case-insensitive)
     */
    @GetMapping
    public ResponseEntity<List<ChatMenuResponse>> getMenus(
            @RequestParam String concept) {

        log.info("Widget menu request for concept={}", concept);
        List<ChatMenuResponse> menus = chatMenuService.getActiveMenusForWidget(concept);
        return ResponseEntity.ok(menus);
    }
}
