package com.lmg.online.chatbot.ai.project.chatmenu.service;

import com.lmg.online.chatbot.ai.project.chatmenu.dto.*;
import com.lmg.online.chatbot.ai.project.chatmenu.entity.ChatMenu;
import com.lmg.online.chatbot.ai.project.chatmenu.entity.ChatSubMenu;
import com.lmg.online.chatbot.ai.project.chatmenu.repository.ChatMenuRepository;
import com.lmg.online.chatbot.ai.project.chatmenu.repository.ChatSubMenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMenuService {

    private final ChatMenuRepository menuRepo;
    private final ChatSubMenuRepository subMenuRepo;

    // ─────────────────────────────────────────────────────────────────────────
    // Widget API  (active items only, inline sub-menus)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all active menus for a concept with their active sub-menus embedded.
     * Used by GET /api/chat/menus?concept=X
     */
    @Transactional(readOnly = true)
    public List<ChatMenuResponse> getActiveMenusForWidget(String concept) {
        return menuRepo
                .findByConceptIgnoreCaseAndActiveTrueOrderByDisplayOrderAsc(concept)
                .stream()
                .map(ChatMenuResponse::fromWidget)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin API  (all items, active + inactive)
    // ─────────────────────────────────────────────────────────────────────────

    /** List all menus across every concept — admin overview. */
    @Transactional(readOnly = true)
    public List<ChatMenuResponse> getAllMenusForAdmin() {
        return menuRepo.findAllByOrderByConceptAscDisplayOrderAsc()
                .stream()
                .map(ChatMenuResponse::fromAdmin)
                .toList();
    }

    /** List all menus for a single concept — admin filtered view. */
    @Transactional(readOnly = true)
    public List<ChatMenuResponse> getMenusByConceptForAdmin(String concept) {
        return menuRepo.findByConceptIgnoreCaseOrderByDisplayOrderAsc(concept)
                .stream()
                .map(ChatMenuResponse::fromAdmin)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Menu CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ChatMenuResponse createMenu(ChatMenuRequest req) {
        // Auto-append if displayOrder not provided (0 = "place at end")
        int order = req.displayOrder() > 0
                ? req.displayOrder()
                : nextMenuOrder(req.concept());

        ChatMenu menu = ChatMenu.builder()
                .concept(req.concept().toUpperCase())
                .title(req.title())
                .icon(req.icon())
                .displayOrder(order)
                .active(req.active())
                .build();

        ChatMenu saved = menuRepo.save(menu);
        log.info("Created ChatMenu id={} concept={} title={}", saved.getId(), saved.getConcept(), saved.getTitle());
        return ChatMenuResponse.fromAdmin(saved);
    }

    @Transactional
    public ChatMenuResponse updateMenu(Long id, ChatMenuRequest req) {
        ChatMenu menu = findMenuOrThrow(id);
        menu.setConcept(req.concept().toUpperCase());
        menu.setTitle(req.title());
        menu.setIcon(req.icon());
        menu.setDisplayOrder(req.displayOrder());
        menu.setActive(req.active());
        log.info("Updated ChatMenu id={}", id);
        return ChatMenuResponse.fromAdmin(menuRepo.save(menu));
    }

    @Transactional
    public void deleteMenu(Long id) {
        ChatMenu menu = findMenuOrThrow(id);
        menuRepo.delete(menu);
        log.info("Deleted ChatMenu id={}", id);
    }

    /**
     * Reorder menus within their concept.
     * {@code req.orderedIds()} is the complete list of menu IDs in desired order.
     */
    @Transactional
    public void reorderMenus(ReorderRequest req) {
        List<Long> ids = req.orderedIds();
        for (int i = 0; i < ids.size(); i++) {
            ChatMenu menu = findMenuOrThrow(ids.get(i));
            menu.setDisplayOrder(i);
            menuRepo.save(menu);
        }
        log.info("Reordered {} menus", ids.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sub-menu CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ChatSubMenuResponse addSubMenu(Long menuId, ChatSubMenuRequest req) {
        ChatMenu menu = findMenuOrThrow(menuId);

        int order = req.displayOrder() > 0
                ? req.displayOrder()
                : nextSubMenuOrder(menuId);

        ChatSubMenu sub = ChatSubMenu.builder()
                .menu(menu)
                .title(req.title())
                .icon(req.icon())
                .intentKey(req.intentKey())
                .displayOrder(order)
                .active(req.active())
                .build();

        ChatSubMenu saved = subMenuRepo.save(sub);
        log.info("Created ChatSubMenu id={} menuId={} intentKey={}", saved.getId(), menuId, saved.getIntentKey());
        return ChatSubMenuResponse.from(saved);
    }

    @Transactional
    public ChatSubMenuResponse updateSubMenu(Long subId, ChatSubMenuRequest req) {
        ChatSubMenu sub = findSubMenuOrThrow(subId);
        sub.setTitle(req.title());
        sub.setIcon(req.icon());
        sub.setIntentKey(req.intentKey());
        sub.setDisplayOrder(req.displayOrder());
        sub.setActive(req.active());
        log.info("Updated ChatSubMenu id={}", subId);
        return ChatSubMenuResponse.from(subMenuRepo.save(sub));
    }

    @Transactional
    public void deleteSubMenu(Long subId) {
        ChatSubMenu sub = findSubMenuOrThrow(subId);
        subMenuRepo.delete(sub);
        log.info("Deleted ChatSubMenu id={}", subId);
    }

    /**
     * Reorder sub-menus within their parent menu.
     * {@code req.orderedIds()} is the complete list of sub-menu IDs in desired order.
     */
    @Transactional
    public void reorderSubMenus(Long menuId, ReorderRequest req) {
        List<Long> ids = req.orderedIds();
        for (int i = 0; i < ids.size(); i++) {
            ChatSubMenu sub = findSubMenuOrThrow(ids.get(i));
            if (!sub.getMenu().getId().equals(menuId)) {
                throw new IllegalArgumentException(
                        "SubMenu id=" + ids.get(i) + " does not belong to menu id=" + menuId);
            }
            sub.setDisplayOrder(i);
            subMenuRepo.save(sub);
        }
        log.info("Reordered {} sub-menus for menuId={}", ids.size(), menuId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ChatMenu findMenuOrThrow(Long id) {
        return menuRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ChatMenu not found: id=" + id));
    }

    private ChatSubMenu findSubMenuOrThrow(Long id) {
        return subMenuRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ChatSubMenu not found: id=" + id));
    }

    private int nextMenuOrder(String concept) {
        return menuRepo.findTopByConceptIgnoreCaseOrderByDisplayOrderDesc(concept)
                .map(max -> max + 1)
                .orElse(0);
    }

    private int nextSubMenuOrder(Long menuId) {
        return subMenuRepo.findTopByMenuIdOrderByDisplayOrderDesc(menuId)
                .map(max -> max + 1)
                .orElse(0);
    }
}
