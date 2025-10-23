package com.lmg.online.chatbot.ai.business.mapper;

import com.lmg.online.chatbot.ai.business.entity.Menu;
import com.lmg.online.chatbot.ai.business.dto.MenuDTO;
import com.lmg.online.chatbot.ai.business.dto.SubMenuDTO;
import com.lmg.online.chatbot.ai.business.entity.SubMenu;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
@Component
public class MenuMapper {
    public MenuDTO toMenuDTO(Menu menu) {
        return MenuDTO.builder()
                .id(menu.getId())
                .title(menu.getTitle())
                .displayOrder(menu.getDisplayOrder())
                .subMenus(toSubMenuDTOList(menu.getSubMenus()))
                .build();
    }

    public List<SubMenuDTO> toSubMenuDTOList(List<SubMenu> subMenus) {
        return subMenus.stream()
                .sorted(Comparator.comparingInt(SubMenu::getDisplayOrder))
                .map(this::toSubMenuDTO)
                .collect(Collectors.toList());
    }

    public SubMenuDTO toSubMenuDTO(SubMenu subMenu) {
        return SubMenuDTO.builder()
                .id(subMenu.getId())
                .title(subMenu.getTitle())
                .type(subMenu.getType())
                .displayOrder(subMenu.getDisplayOrder())
                .build();
    }

}
