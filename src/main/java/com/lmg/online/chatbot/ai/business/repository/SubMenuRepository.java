package com.lmg.online.chatbot.ai.business.repository;


import com.lmg.online.chatbot.ai.business.entity.SubMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubMenuRepository extends JpaRepository<SubMenu, Long> {

    /**
     * Fetch submenus for a given menu, ordered by display order.
     */
    List<SubMenu> findByMenuIdOrderByDisplayOrderAsc(Long menuId);

    /**
     * Optional: fetch only active submenus if you add an 'active' flag later.
     */
    List<SubMenu> findByMenuIdAndActiveTrueOrderByDisplayOrderAsc(Long menuId);



    // Find active submenus ordered by display order
    List<SubMenu> findByActiveOrderByDisplayOrderAsc(Boolean active);

    // Find submenus by type
    List<SubMenu> findByType(String type);

    // Find submenus by menu ID and type
    List<SubMenu> findByMenuIdAndType(Long menuId, String type);

    // Find active submenus by menu ID
    List<SubMenu> findByMenuIdAndActiveOrderByDisplayOrderAsc(Long menuId, Boolean active);

    // Find submenu by title and menu ID
    Optional<SubMenu> findByTitleAndMenuId(String title, Long menuId);

    // Check if submenu exists by title and menu ID
    boolean existsByTitleAndMenuId(String title, Long menuId);

    // Count submenus by menu ID
    long countByMenuId(Long menuId);

    // Count active submenus by menu ID
    long countByMenuIdAndActive(Long menuId, Boolean active);

    // Delete all submenus by menu ID
    void deleteByMenuId(Long menuId);

    // Find all submenus with their menu (eager loading)
    @Query("SELECT s FROM SubMenu s JOIN FETCH s.menu ORDER BY s.displayOrder")
    List<SubMenu> findAllWithMenu();
}