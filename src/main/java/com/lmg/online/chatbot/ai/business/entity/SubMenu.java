package com.lmg.online.chatbot.ai.business.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubMenu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;         // e.g., "Order", "Return Policy"
    private String type;          // "static" or "dynamic
       private String url;
    private String responseFormatSchema;
    private boolean active;
    // Relationship: Many submenus belong to one menu
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"subMenus", "hibernateLazyInitializer", "handler"})
    @JoinColumn(name = "menu_id") // foreign key in sub_menus table
    private Menu menu;
    private int  displayOrder;
}

