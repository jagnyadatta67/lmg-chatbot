package com.lmg.online.chatbot.ai.project.chatmenu.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a top-level chat menu item, scoped to a brand concept
 * (e.g. HOMECENTRE, LIFESTYLE, MAX, BABYSHOP).
 *
 * Table: chat_menus
 */
@Entity
@Table(
        name = "chat_menus",
        indexes = {
                @Index(name = "idx_chat_menus_concept", columnList = "concept"),
                @Index(name = "idx_chat_menus_concept_order", columnList = "concept, displayOrder")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Brand concept this menu belongs to, e.g. "HOMECENTRE", "MAX".
     * Case-insensitive lookups are normalised to UPPER by the service.
     */
    @Column(nullable = false, length = 50)
    private String concept;

    /** Display label shown in the chatbot widget, e.g. "Track My Order". */
    @Column(nullable = false, length = 150)
    private String title;

    /** Optional emoji or icon code shown before the title in the widget. */
    @Column(length = 10, columnDefinition = "VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String icon;

    /** Sort position within the concept; lower = displayed first. */
    @Column(nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    /** Soft-delete — inactive menus are excluded from widget responses. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Set automatically on first persist; never updated. */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Updated on every modification via @PreUpdate. */
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Sub-menus that belong to this menu.
     * Fetched eagerly so the widget gets the full tree in one query.
     */
    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<ChatSubMenu> subMenus = new ArrayList<>();

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
