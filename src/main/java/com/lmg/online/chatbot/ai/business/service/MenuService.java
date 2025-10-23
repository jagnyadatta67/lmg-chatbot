package com.lmg.online.chatbot.ai.business.service;

import com.lmg.online.chatbot.ai.business.entity.Menu;
import com.lmg.online.chatbot.ai.business.entity.SubMenu;
import com.lmg.online.chatbot.ai.business.repository.MenuRepository;
import com.lmg.online.chatbot.ai.business.repository.SubMenuRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MenuService {

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private SubMenuRepository subMenuRepository;

    public List<Menu> getAllMenus() {
        return menuRepository.findAllActiveMenusWithSubMenus();
    }

    public List<SubMenu> getSubMenus(Long menuId) {
        return subMenuRepository.findByMenuIdOrderByDisplayOrderAsc(menuId);
    }
}
