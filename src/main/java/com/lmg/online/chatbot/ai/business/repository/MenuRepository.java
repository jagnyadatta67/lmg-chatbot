package com.lmg.online.chatbot.ai.business.repository;




import com.lmg.online.chatbot.ai.business.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {

    /**
     * Fetch only active menus ordered by display order.
     */
    List<Menu> findAllByActiveTrueOrderByDisplayOrderAsc();

    /**
     * Fetch all menus with their submenus in one query (prevents N+1 problem).
     */
    @Query("SELECT DISTINCT m FROM Menu m LEFT JOIN FETCH m.subMenus WHERE m.active = true ORDER BY m.displayOrder ASC")
    List<Menu> findAllActiveMenusWithSubMenus();

    List<Menu> findAllByOrderByDisplayOrderAsc();

    // Find active menus ordered by display order
    List<Menu> findByActiveOrderByDisplayOrderAsc(Boolean active);

    // Find menu by ID with submenus (eager loading)
    @Query("SELECT m FROM Menu m LEFT JOIN FETCH m.subMenus WHERE m.id = :id")
    Optional<Menu> findByIdWithSubMenus(Long id);

    // Find all menus with submenus (eager loading)
    @Query("SELECT DISTINCT m FROM Menu m LEFT JOIN FETCH m.subMenus ORDER BY m.displayOrder")
    List<Menu> findAllWithSubMenus();

    // Find menu by title
    Optional<Menu> findByTitle(String title);

    // Check if menu exists by title
    boolean existsByTitle(String title);

    // Count active menus
    long countByActive(Boolean active);
}
