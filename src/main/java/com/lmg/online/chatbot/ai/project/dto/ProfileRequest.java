package com.lmg.online.chatbot.ai.project.dto;

import lombok.Data;

/**
 * Request DTO for the GET /api/user/profile endpoint.
 *
 * <pre>
 * {
 *   "token"   : "4fca0708-d072-47de-b819-3c18311c824d",  // access_token from the user session
 *   "concept" : "LIFESTYLE",
 *   "env"     : "uat1",
 *   "appId"   : "Desktop"
 * }
 * </pre>
 */
@Data
public class ProfileRequest {

    /**
     * The user's session access_token (UUID format, obtained after login).
     * Passed as the {@code access_token} header to the commerce API.
     */
    private String token;

    /**
     * Brand concept: LIFESTYLE | MAX | BABYSHOP | HOMECENTRE
     */
    private String concept;

    /**
     * Environment prefix: uat1 | uat5 | stg | prod (null / blank = prod)
     */
    private String env;

    /**
     * Application type: Desktop | Mobile | ANDROID | IPHONE  (default: Desktop)
     */
    private String appId;
}
