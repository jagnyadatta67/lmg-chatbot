package com.lmg.online.chatbot.ai.project.chatmenu.repository;

import com.lmg.online.chatbot.ai.project.chatmenu.entity.ChatSubMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSubMenuRepository extends JpaRepository<ChatSubMenu, Long> {

    /** All active sub-menus for a parent menu, ordered by displayOrder. */
    List<ChatSubMenu> findByMenuIdAndActiveTrueOrderByDisplayOrderAsc(Long menuId);

    /** All sub-menus for a parent menu (active + inactive) — used by admin. */
    List<ChatSubMenu> findByMenuIdOrderByDisplayOrderAsc(Long menuId);

    /** Max displayOrder for a parent menu so new items are appended. */
    @Query("SELECT MAX(s.displayOrder) FROM ChatSubMenu s WHERE s.menu.id = :menuId")
    Optional<Integer> findTopByMenuIdOrderByDisplayOrderDesc(@Param("menuId") Long menuId);
}
