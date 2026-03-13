package com.lmg.online.chatbot.ai.project.chatmenu.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Represents a sub-menu item nested under a {@link ChatMenu}.
 *
 * The {@code intentKey} field is an open string that the chatbot widget
 * uses to route the user's action (e.g. "TRACK_ORDER", "GIFT_CARD_BALANCE",
 * "NEARBY_STORE").  Any value is valid; the widget maps known keys to
 * specialised handlers and falls back to free-text input for unknown ones.
 *
 * Table: chat_sub_menus
 */
@Entity
@Table(
        name = "chat_sub_menus",
        indexes = {
                @Index(name = "idx_chat_sub_menus_menu", columnList = "menu_id"),
                @Index(name = "idx_chat_sub_menus_order", columnList = "menu_id, displayOrder")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSubMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Parent menu — mandatory. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private ChatMenu menu;

    /** Display label shown in the chatbot widget, e.g. "Track my order". */
    @Column(nullable = false, length = 150)
    private String title;

    /** Optional emoji or icon code shown before the title in the widget. */
    @Column(length = 10, columnDefinition = "VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String icon;

    /**
     * Open-string intent key used by the widget's dispatch map.
     * Examples: "TRACK_ORDER", "GIFT_CARD_BALANCE", "NEARBY_STORE".
     * Unknown keys cause the widget to show a free-text input.
     * Max 100 chars — arbitrary but keeps DB tidy.
     */
    @Column(length = 100)
    private String intentKey;

    /** Sort position within the parent menu; lower = displayed first. */
    @Column(nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    /** Soft-delete — inactive sub-menus are excluded from widget responses. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Set automatically on first persist; never updated. */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Updated on every modification via @PreUpdate. */
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
