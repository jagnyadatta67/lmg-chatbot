package com.lmg.online.chatbot.ai.project.chatmenu.controller;

import com.lmg.online.chatbot.ai.project.chatmenu.dto.*;
import com.lmg.online.chatbot.ai.project.chatmenu.service.ChatMenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin REST API for managing the dynamic chat menu.
 *
 * All endpoints live under /api/admin/chatmenu.
 * The existing ApiKeyAuthFilter already protects all /api/admin/** routes,
 * so no additional security config is needed.
 *
 * ── Menus ──────────────────────────────────────────────────────────────────
 *   GET    /api/admin/chatmenu/menus                  list all menus (all concepts)
 *   GET    /api/admin/chatmenu/menus?concept=X         list menus for concept
 *   POST   /api/admin/chatmenu/menus                  create menu
 *   PUT    /api/admin/chatmenu/menus/{id}              update menu
 *   DELETE /api/admin/chatmenu/menus/{id}              delete menu
 *   POST   /api/admin/chatmenu/menus/reorder           reorder menus
 *
 * ── Sub-menus ───────────────────────────────────────────────────────────────
 *   POST   /api/admin/chatmenu/menus/{menuId}/submenus          add sub-menu
 *   PUT    /api/admin/chatmenu/submenus/{subId}                  update sub-menu
 *   DELETE /api/admin/chatmenu/submenus/{subId}                  delete sub-menu
 *   POST   /api/admin/chatmenu/menus/{menuId}/submenus/reorder   reorder sub-menus
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/chatmenu")
@RequiredArgsConstructor
public class ChatMenuAdminController {

    private final ChatMenuService chatMenuService;

    // ─────────────────────────────────────────────────────────────────────────
    // Menus
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/menus")
    public ResponseEntity<List<ChatMenuResponse>> listMenus(
            @RequestParam(required = false) String concept) {

        List<ChatMenuResponse> result = (concept != null && !concept.isBlank())
                ? chatMenuService.getMenusByConceptForAdmin(concept)
                : chatMenuService.getAllMenusForAdmin();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/menus")
    public ResponseEntity<ChatMenuResponse> createMenu(
            @Valid @RequestBody ChatMenuRequest req) {

        ChatMenuResponse created = chatMenuService.createMenu(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/menus/{id}")
    public ResponseEntity<ChatMenuResponse> updateMenu(
            @PathVariable Long id,
            @Valid @RequestBody ChatMenuRequest req) {

        return ResponseEntity.ok(chatMenuService.updateMenu(id, req));
    }

    @DeleteMapping("/menus/{id}")
    public ResponseEntity<Void> deleteMenu(@PathVariable Long id) {
        chatMenuService.deleteMenu(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/menus/reorder")
    public ResponseEntity<Void> reorderMenus(@Valid @RequestBody ReorderRequest req) {
        chatMenuService.reorderMenus(req);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sub-menus
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/menus/{menuId}/submenus")
    public ResponseEntity<ChatSubMenuResponse> addSubMenu(
            @PathVariable Long menuId,
            @Valid @RequestBody ChatSubMenuRequest req) {

        ChatSubMenuResponse created = chatMenuService.addSubMenu(menuId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/submenus/{subId}")
    public ResponseEntity<ChatSubMenuResponse> updateSubMenu(
            @PathVariable Long subId,
            @Valid @RequestBody ChatSubMenuRequest req) {

        return ResponseEntity.ok(chatMenuService.updateSubMenu(subId, req));
    }

    @DeleteMapping("/submenus/{subId}")
    public ResponseEntity<Void> deleteSubMenu(@PathVariable Long subId) {
        chatMenuService.deleteSubMenu(subId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/menus/{menuId}/submenus/reorder")
    public ResponseEntity<Void> reorderSubMenus(
            @PathVariable Long menuId,
            @Valid @RequestBody ReorderRequest req) {

        chatMenuService.reorderSubMenus(menuId, req);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Exception handler
    // ─────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "status",    400,
                "error",     "Bad Request",
                "message",   ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
