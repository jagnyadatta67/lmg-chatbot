package com.lmg.online.chatbot.ai.business.facade;

import com.lmg.online.chatbot.ai.business.dto.MenuDTO;
import com.lmg.online.chatbot.ai.business.dto.SubMenuDTO;
import com.lmg.online.chatbot.ai.business.mapper.MenuMapper;
import com.lmg.online.chatbot.ai.business.service.MenuService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class MenuFacade {

    private final MenuService menuService;
    private final MenuMapper menuMapper;

    public List<MenuDTO> getAllMenus() {
        return menuService.getAllMenus().stream()
                .map(menuMapper::toMenuDTO)
                .toList();
    }

    public List<SubMenuDTO> getSubMenusByMenu(Long menuId) {
        return menuService.getSubMenus(menuId).stream()
                .map(menu -> menuMapper.toSubMenuDTO(menu)).collect(Collectors.toList());

    }

}
