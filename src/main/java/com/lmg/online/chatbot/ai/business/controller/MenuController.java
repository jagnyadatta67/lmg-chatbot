package com.lmg.online.chatbot.ai.business.controller;


import com.lmg.online.chatbot.ai.business.dto.MenuDTO;
import com.lmg.online.chatbot.ai.business.dto.SubMenuDTO;
import com.lmg.online.chatbot.ai.business.facade.MenuFacade;
import com.lmg.online.chatbot.ai.business.service.PdfQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("api/chat")
@CrossOrigin(origins = "*")
@Tag(name = "Chatbot Menu API", description = "Operations for chatbot menus and submenus")
@SecurityRequirement(name = "openid")
public class MenuController {

    private final MenuFacade menuFacade;

    public MenuController(MenuFacade menuFacade, PdfQueryService pdfQueryService) {
        this.menuFacade = menuFacade;
        this.pdfQueryService = pdfQueryService;
    }

    @Operation(
            summary = "Get all main menus with their submenus",
            description = "Returns the list of all active menus including their submenus (static/dynamic)"
    )

    @GetMapping("/menus")
    public List<MenuDTO> getAllMenus() {
        return menuFacade.getAllMenus();
    }

    @Operation(
            summary = "Get submenus by menu ID",
            description = "Returns all submenus for a specific menu, ordered by display order"
    )
    @GetMapping("/menus/{menuId}/submenus")
    public List<SubMenuDTO> getSubMenus(@PathVariable Long menuId) {
        return menuFacade.getSubMenusByMenu(menuId);
    }


    private final PdfQueryService pdfQueryService;




    public AnswerResponse ask(@RequestBody QuestionRequest request) {
        String answer = pdfQueryService.askQuestion(request.question());
        return new AnswerResponse(answer);
    }

    @GetMapping("/ask")
    public AnswerResponse askGet(@RequestParam String question) {
        String answer = pdfQueryService.askQuestion(question);
        return new AnswerResponse(answer);
    }

    record QuestionRequest(String question) {}
    record AnswerResponse(String chat_message) {}
}