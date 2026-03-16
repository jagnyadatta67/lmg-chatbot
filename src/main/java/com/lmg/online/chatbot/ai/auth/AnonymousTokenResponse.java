package com.lmg.online.chatbot.ai.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deserialises the Hybris OAuth2 client-credentials token response.
 *
 * <pre>
 * POST /landmarkshopscommercews/in/oauth/token
 * Content-Type: application/x-www-form-urlencoded
 *
 * Response:
 * {
 *   "access_token": "abc123...",
 *   "token_type":   "bearer",
 *   "expires_in":   3600,
 *   "scope":        "basic"
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnonymousTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    /** Lifetime in seconds (Hybris default: 3600) */
    @JsonProperty("expires_in")
    private int expiresIn;

    @JsonProperty("scope")
    private String scope;
}
