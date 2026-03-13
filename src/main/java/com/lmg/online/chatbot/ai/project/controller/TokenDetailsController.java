package com.lmg.online.chatbot.ai.project.controller;

import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.user.TokenDetailsService;
import com.lmg.online.chatbot.ai.tools.user.dto.TokenDetailsRequest;
import com.lmg.online.chatbot.ai.tools.user.dto.UserWsDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller that resolves a commerce-platform token to a user profile.
 *
 * <p>Two endpoints are exposed:
 * <ul>
 *   <li>{@code POST /api/user/token-details} – dedicated DTO (preferred)</li>
 *   <li>{@code POST /api/user/token-details/chat} – accepts the shared
 *       {@link ChatRequest} body for callers that already construct one;
 *       maps {@code userId} → token, {@code appid} → appId</li>
 * </ul>
 *
 * <p>Both delegate to {@link TokenDetailsService}, which calls:
 * <pre>
 *   POST {baseUrl}/landmarkshopscommercews/v2/{siteId}/chatbot/getTokenDetails
 *        ?token={token}&appId={appId}
 * </pre>
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class TokenDetailsController {

    private final TokenDetailsService tokenDetailsService;

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 1 – dedicated TokenDetailsRequest (preferred)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolve a user token to a {@link UserWsDTO}.
     *
     * <p>Example request body:
     * <pre>{@code
     * {
     *   "token"   : "w24/U2l2vjauA2FAaQ5LXvPCIAQ...",
     *   "concept" : "LIFESTYLE",
     *   "env"     : "uat1",
     *   "appId"   : "Desktop"
     * }
     * }</pre>
     *
     * @param request validated {@link TokenDetailsRequest}
     * @return {@link UserWsDTO} with uid, firstName, lastName, email, gender, signInMobile
     */
    @PostMapping("/token-details")
    public ResponseEntity<UserWsDTO> getTokenDetails(
            @Valid @RequestBody TokenDetailsRequest request) {

        log.info("📨 token-details → concept={}, env={}, appId={}",
                request.getConcept(), request.getEnv(), request.getAppId());

        UserWsDTO userWsDTO = tokenDetailsService.getTokenDetails(
                request.getConcept(),
                request.getEnv(),
                request.getToken(),
                request.getAppId()
        );

        return ResponseEntity.ok(userWsDTO);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 2 – ChatRequest variant (for callers that already build one)
    //              userId  → token
    //              appid   → appId
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Same as {@link #getTokenDetails(TokenDetailsRequest)} but accepts the
     * shared {@link ChatRequest} body.
     *
     * <p>Field mapping:
     * <ul>
     *   <li>{@code userId}  → token</li>
     *   <li>{@code concept} → concept</li>
     *   <li>{@code env}     → env</li>
     *   <li>{@code appid}   → appId</li>
     * </ul>
     *
     * <p>Example request body:
     * <pre>{@code
     * {
     *   "message"  : "-",
     *   "userId"   : "w24/U2l2vjauA2FAaQ5LXvPCIAQ...",
     *   "concept"  : "LIFESTYLE",
     *   "env"      : "uat1",
     *   "appid"    : "Desktop"
     * }
     * }</pre>
     *
     * @param request {@link ChatRequest} – {@code userId} carries the token
     * @return {@link UserWsDTO}
     */
    @PostMapping("/token-details/chat")
    public ResponseEntity<UserWsDTO> getTokenDetailsFromChatRequest(
            @Valid @RequestBody ChatRequest request) {

        log.info("📨 token-details/chat → concept={}, env={}, appId={}",
                request.getConcept(), request.getEnv(), request.getAppid());

        UserWsDTO userWsDTO = tokenDetailsService.getTokenDetails(
                request.getConcept(),
                request.getEnv(),
                request.getUserId(),     // userId field carries the encrypted token
                request.getAppid()
        );

        return ResponseEntity.ok(userWsDTO);
    }
}
