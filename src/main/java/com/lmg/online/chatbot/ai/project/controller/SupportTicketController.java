package com.lmg.online.chatbot.ai.project.controller;

import com.lmg.online.chatbot.ai.project.service.SupportTicketService;
import com.lmg.online.chatbot.ai.tools.support.dto.SupportTicketRequest;
import com.lmg.online.chatbot.ai.tools.support.dto.SupportTicketResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * POST /api/support/ticket
 *
 * Accepts a Write-to-Us form submission from the chatbot widget,
 * sends an HTML support email, and returns a ticket confirmation.
 *
 * Protected by X-API-Key (same as all /api/** endpoints).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/support")
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    @PostMapping("/ticket")
    public ResponseEntity<SupportTicketResponse> submitTicket(
            @Valid @RequestBody SupportTicketRequest request) {

        log.info("📩 Support ticket received — concept={}, category={}, user={}",
                request.getConcept(), request.getCategory(), request.getUserId());

        SupportTicketResponse response = SupportTicketResponse.builder().success(true).build();
                supportTicketService.submitTicket(request);

        return ResponseEntity.ok(response);
    }
}
