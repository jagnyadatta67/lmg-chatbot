package com.lmg.online.chatbot.ai.project.controller;

import com.lmg.online.chatbot.ai.project.dto.ProfileRequest;
import com.lmg.online.chatbot.ai.project.service.UserProfileService;
import com.lmg.online.chatbot.ai.request.ChatRequest;


import com.lmg.online.chatbot.ai.tools.user.dto.UserWsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller that exposes the commerce platform's v3 user-profile API.
 *
 * <p>Two variants are provided:
 * <ol>
 *   <li>{@code POST /api/user/profile}       – dedicated {@link ProfileRequest} body (preferred)</li>
 *   <li>{@code POST /api/user/profile/chat}  – reuses existing {@link ChatRequest}
 *       ({@code userId} = access_token)</li>
 * </ol>
 *
 * <p>External API called:
 * <pre>
 * GET /landmarkshopscommercews/v3/{siteId}/en/users/current/profile?appId={appId}
 * Header: access_token: {token}
 * </pre>
 */
@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserProfileService userProfileService;

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 1 – dedicated ProfileRequest body (preferred)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetch the logged-in user's profile using a dedicated request body.
     *
     * <pre>
     * POST /api/user/profile
     * {
     *   "token"   : "4fca0708-d072-47de-b819-3c18311c824d",
     *   "concept" : "LIFESTYLE",
     *   "env"     : "uat1",
     *   "appId"   : "Desktop"
     * }
     * </pre>
     */
    @PostMapping
    public ResponseEntity<UserWsDTO> getProfile(@RequestBody ProfileRequest request) {

        log.info("[UserProfileController] getProfile → concept={} env={} appId={}",
                request.getConcept(), request.getEnv(), request.getAppId());

        UserWsDTO profile = userProfileService.fetchProfile(
                request.getConcept(),
                request.getEnv(),
                request.getToken(),
                request.getAppId()
        );

        return ResponseEntity.ok(profile);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 2 – ChatRequest variant  (userId = access_token)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetch the logged-in user's profile via the existing {@link ChatRequest} shape.
     * Map: {@code userId} → access_token, {@code appid} → appId.
     *
     * <pre>
     * POST /api/user/profile/chat
     * {
     *   "message" : "-",
     *   "userId"  : "4fca0708-d072-47de-b819-3c18311c824d",
     *   "concept" : "LIFESTYLE",
     *   "env"     : "uat1",
     *   "appid"   : "Desktop"
     * }
     * </pre>
     */
    @PostMapping("/chat")
    public ResponseEntity<UserWsDTO> getProfileViaChat(@RequestBody ChatRequest request) {

        log.info("[UserProfileController] getProfile/chat → concept={} env={} appid={}",
                request.getConcept(), request.getEnv(), request.getAppid());

        UserWsDTO profile = userProfileService.fetchProfile(
                request.getConcept(),
                request.getEnv(),
                request.getUserId(),     // userId carries the access_token
                request.getAppid()
        );

        return ResponseEntity.ok(profile);
    }
}
