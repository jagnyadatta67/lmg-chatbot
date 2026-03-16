package com.lmg.online.chatbot.ai.project.config;

import com.lmg.online.chatbot.ai.project.chatmenu.entity.ChatMenu;
import com.lmg.online.chatbot.ai.project.chatmenu.entity.ChatSubMenu;
import com.lmg.online.chatbot.ai.project.chatmenu.repository.ChatMenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds default chat menus and sub-menus for all brand concepts on first startup.
 *
 * Runs only when NO menus exist in the database (idempotent).
 * Covers all 4 intents from the chatbot API specification:
 *   ORDER_LISTING, DELIVERY_TRACKING, RETURN_STATUS, WALLET_BALANCE
 * Plus existing intents:
 *   TRACK_ORDER, CUSTOMER_PROFILE, GIFT_CARD_BALANCE, NEARBY_STORE, POLICY_QUESTION
 *
 * Menu structure (same for all concepts):
 *
 *  📦 My Orders
 *    ├─ 📋 Order History        → ORDER_LISTING
 *    ├─ 🔍 Track My Order       → TRACK_ORDER
 *    ├─ 🚚 Delivery Status      → DELIVERY_TRACKING
 *    └─ ↩️  Return / Refund      → RETURN_STATUS
 *
 *  💳 My Account
 *    ├─ 👤 My Profile           → CUSTOMER_PROFILE
 *    ├─ 💰 Wallet Balance        → WALLET_BALANCE
 *    └─ 🎁 Gift Card Balance     → GIFT_CARD_BALANCE
 *
 *  🏪 Store & Support
 *    ├─ 📍 Find Nearby Store     → NEARBY_STORE
 *    └─ 📜 Policies & FAQs       → POLICY_QUESTION
 */
@Slf4j
@Component
@Order(2)   // runs after DataInitializer (Order 1 by default)
@RequiredArgsConstructor
public class MenuDataInitializer implements CommandLineRunner {

    private static final List<String> CONCEPTS =
            List.of("LIFESTYLE", "MAX", "HOMECENTRE", "BABYSHOP");

    private final ChatMenuRepository menuRepo;

    @Override
    @Transactional
    public void run(String... args) {
        long existing = menuRepo.count();
        if (existing > 0) {
            log.info("MenuDataInitializer: {} menu(s) already exist — skipping seed.", existing);
            return;
        }

        log.info("MenuDataInitializer: seeding default menus for {} concepts...", CONCEPTS.size());

        for (String concept : CONCEPTS) {
            seedConcept(concept);
        }

        log.info("MenuDataInitializer: ✅ Seeded {} menus across {} concepts.",
                menuRepo.count(), CONCEPTS.size());
    }

    private void seedConcept(String concept) {
        // ── Menu 1: My Orders ───────────────────────────────────────────────
        ChatMenu orders = buildMenu(concept, "My Orders", "", 1);
        orders.getSubMenus().addAll(List.of(
                buildSub(orders, "Order History",   "", "ORDER_LISTING",     1),
                buildSub(orders, "Track My Order",  "", "TRACK_ORDER",       2),
                buildSub(orders, "Delivery Status", "", "DELIVERY_TRACKING", 3),
                buildSub(orders, "Return / Refund", "", "RETURN_STATUS",     4)
        ));
        menuRepo.save(orders);

        // ── Menu 2: My Account ──────────────────────────────────────────────
        ChatMenu account = buildMenu(concept, "My Account", "", 2);
        account.getSubMenus().addAll(List.of(
                buildSub(account, "My Profile",        "", "CUSTOMER_PROFILE",  1),
                buildSub(account, "Wallet Balance",    "", "WALLET_BALANCE",     2),
                buildSub(account, "Gift Card Balance", "", "GIFT_CARD_BALANCE",  3)
        ));
        menuRepo.save(account);

        // ── Menu 3: Store & Support ─────────────────────────────────────────
        ChatMenu support = buildMenu(concept, "Store & Support", "", 3);
        support.getSubMenus().addAll(List.of(
                buildSub(support, "Find Nearby Store", "", "NEARBY_STORE",    1),
                buildSub(support, "Policies & FAQs",   "", "POLICY_QUESTION", 2),
                buildSub(support, "Write Us",   "", "TICKET", 2)
        ));
        menuRepo.save(support);

        log.info("  ✔ Seeded 3 menus + 9 sub-menus for concept: {}", concept);
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private ChatMenu buildMenu(String concept, String title, String icon, int order) {
        return ChatMenu.builder()
                .concept(concept)
                .title(title)
                .icon(icon)
                .displayOrder(order)
                .active(true)
                .build();
    }

    private ChatSubMenu buildSub(ChatMenu menu, String title, String icon,
                                  String intentKey, int order) {
        return ChatSubMenu.builder()
                .menu(menu)
                .title(title)
                .icon(icon)
                .intentKey(intentKey)
                .displayOrder(order)
                .active(true)
                .build();
    }
}
