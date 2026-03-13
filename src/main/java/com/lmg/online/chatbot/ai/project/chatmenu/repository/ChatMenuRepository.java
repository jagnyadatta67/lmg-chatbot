package com.lmg.online.chatbot.ai.project.chatmenu.repository;

import com.lmg.online.chatbot.ai.project.chatmenu.entity.ChatMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMenuRepository extends JpaRepository<ChatMenu, Long> {

    /** All active menus for a concept, ordered by displayOrder ascending. */
    List<ChatMenu> findByConceptIgnoreCaseAndActiveTrueOrderByDisplayOrderAsc(String concept);

    /** All menus for a concept (active + inactive) — used by admin. */
    List<ChatMenu> findByConceptIgnoreCaseOrderByDisplayOrderAsc(String concept);

    /** All menus across all concepts — used by admin list-all. */
    List<ChatMenu> findAllByOrderByConceptAscDisplayOrderAsc();

    /** Max displayOrder for a concept so new items are appended at the end. */
    @Query("SELECT MAX(m.displayOrder) FROM ChatMenu m WHERE LOWER(m.concept) = LOWER(:concept)")
    Optional<Integer> findTopByConceptIgnoreCaseOrderByDisplayOrderDesc(@Param("concept") String concept);
}
