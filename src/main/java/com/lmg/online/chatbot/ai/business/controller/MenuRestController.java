package com.lmg.online.chatbot.ai.business.controller;

import com.lmg.online.chatbot.ai.business.entity.Menu;
import com.lmg.online.chatbot.ai.business.entity.SubMenu;
import com.lmg.online.chatbot.ai.business.repository.MenuRepository;
import com.lmg.online.chatbot.ai.business.repository.SubMenuRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menu")
@CrossOrigin(origins = "*")
public class MenuRestController {

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private SubMenuRepository subMenuRepository;

    // Get all menus with submenus
    @GetMapping
    public ResponseEntity<List<Menu>> getAllMenus() {
        List<Menu> menus = menuRepository.findAllWithSubMenus();
        return ResponseEntity.ok(menus);
    }

    // Get menu by ID
    @GetMapping("/{id}")
    public ResponseEntity<Menu> getMenuById(@PathVariable Long id) {
        return menuRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Add new menu
    @PostMapping
    public ResponseEntity<Map<String, Object>> addMenu(@RequestBody Menu menu) {
        Map<String, Object> response = new HashMap<>();
        try {
            Menu savedMenu = menuRepository.save(menu);
            response.put("success", true);
            response.put("message", "Menu added successfully!");
            response.put("data", savedMenu);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error adding menu: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Update menu
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateMenu(@PathVariable Long id, @RequestBody Menu menu) {
        Map<String, Object> response = new HashMap<>();
        try {
            return menuRepository.findById(id)
                    .map(existingMenu -> {
                        existingMenu.setTitle(menu.getTitle());
                        existingMenu.setActive(menu.isActive());
                        existingMenu.setDisplayOrder(menu.getDisplayOrder());
                        Menu updatedMenu = menuRepository.save(existingMenu);
                        response.put("success", true);
                        response.put("message", "Menu updated successfully!");
                        response.put("data", updatedMenu);
                        return ResponseEntity.ok(response);
                    })
                    .orElseGet(() -> {
                        response.put("success", false);
                        response.put("message", "Menu not found");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                    });
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating menu: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Delete menu
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMenu(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (menuRepository.existsById(id)) {
                menuRepository.deleteById(id);
                response.put("success", true);
                response.put("message", "Menu deleted successfully!");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Menu not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error deleting menu: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Toggle menu status
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleMenuStatus(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            return menuRepository.findById(id)
                    .map(menu -> {
                        menu.setActive(!menu.isActive());
                        Menu updatedMenu = menuRepository.save(menu);
                        response.put("success", true);
                        response.put("message", "Menu status updated!");
                        response.put("data", updatedMenu);
                        return ResponseEntity.ok(response);
                    })
                    .orElseGet(() -> {
                        response.put("success", false);
                        response.put("message", "Menu not found");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                    });
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating menu: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // ================== SUBMENU ENDPOINTS ==================

    // Get all submenus
    @GetMapping("/submenu")
    public ResponseEntity<List<SubMenu>> getAllSubMenus() {
        List<SubMenu> subMenus = subMenuRepository.findAll();
        return ResponseEntity.ok(subMenus);
    }

    // Get submenus by menu ID
    @GetMapping("/{menuId}/submenu")
    public ResponseEntity<List<SubMenu>> getSubMenusByMenuId(@PathVariable Long menuId) {
        List<SubMenu> subMenus = subMenuRepository.findByMenuIdOrderByDisplayOrderAsc(menuId);
        return ResponseEntity.ok(subMenus);
    }

    // Add new submenu
    @PostMapping("/{menuId}/submenu")
    public ResponseEntity<Map<String, Object>> addSubMenu(@PathVariable Long menuId, @RequestBody SubMenu subMenu) {
        Map<String, Object> response = new HashMap<>();
        try {
            return menuRepository.findById(menuId)
                    .map(menu -> {
                        subMenu.setMenu(menu);
                        SubMenu savedSubMenu = subMenuRepository.save(subMenu);
                        response.put("success", true);
                        response.put("message", "SubMenu added successfully!");
                        response.put("data", savedSubMenu);
                        return ResponseEntity.status(HttpStatus.CREATED).body(response);
                    })
                    .orElseGet(() -> {
                        response.put("success", false);
                        response.put("message", "Menu not found");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                    });
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error adding submenu: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Update submenu
    @PutMapping("/submenu/{id}")
    public ResponseEntity<Map<String, Object>> updateSubMenu(@PathVariable Long id, @RequestBody SubMenu subMenu) {
        Map<String, Object> response = new HashMap<>();
        try {
            return subMenuRepository.findById(id)
                    .map(existingSubMenu -> {
                        existingSubMenu.setTitle(subMenu.getTitle());
                        existingSubMenu.setType(subMenu.getType());
                        existingSubMenu.setActive(subMenu.isActive());
                        existingSubMenu.setDisplayOrder(subMenu.getDisplayOrder());
                        SubMenu updatedSubMenu = subMenuRepository.save(existingSubMenu);
                        response.put("success", true);
                        response.put("message", "SubMenu updated successfully!");
                        response.put("data", updatedSubMenu);
                        return ResponseEntity.ok(response);
                    })
                    .orElseGet(() -> {
                        response.put("success", false);
                        response.put("message", "SubMenu not found");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                    });
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating submenu: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Delete submenu
    @DeleteMapping("/submenu/{id}")
    public ResponseEntity<Map<String, Object>> deleteSubMenu(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (subMenuRepository.existsById(id)) {
                subMenuRepository.deleteById(id);
                response.put("success", true);
                response.put("message", "SubMenu deleted successfully!");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "SubMenu not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error deleting submenu: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Toggle submenu status
    @PatchMapping("/submenu/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleSubMenuStatus(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            return subMenuRepository.findById(id)
                    .map(subMenu -> {
                        subMenu.setActive(!subMenu.isActive());
                        SubMenu updatedSubMenu = subMenuRepository.save(subMenu);
                        response.put("success", true);
                        response.put("message", "SubMenu status updated!");
                        response.put("data", updatedSubMenu);
                        return ResponseEntity.ok(response);
                    })
                    .orElseGet(() -> {
                        response.put("success", false);
                        response.put("message", "SubMenu not found");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                    });
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating submenu: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}