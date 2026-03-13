package com.lmg.online.chatbot.ai.tools.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Request DTO for the /api/user/token-details endpoint.
 *
 * <p>Maps directly to the external
 * {@code POST /landmarkshopscommercews/v2/{siteId}/chatbot/getTokenDetails?token=&appId=}
 * call.</p>
 */
@Data
public class TokenDetailsRequest {

    /**
     * Encrypted user token issued by the commerce platform.
     *
     * The token is normalised on ingestion: if it arrives already percent-encoded
     * (e.g. w24%2FU2l2v...%3D%3D from a browser data-* attribute) it is decoded
     * to its raw form (w24/U2l2v...==) so all downstream code works with plain text
     * and never double-encodes it.
     *
     * Lombok setter suppressed — use the custom {@link #setToken(String)} below.
     */
    @Setter(AccessLevel.NONE)
    @NotBlank(message = "token must not be blank")
    private String token;

    /**
     * Normalises the token to plain (decoded) text on set.
     * Works whether the caller sends a raw token or a percent-encoded one.
     */
    public void setToken(String token) {
        if (token == null) { this.token = null; return; }
        try {
            this.token = URLDecoder.decode(token, StandardCharsets.UTF_8);
        } catch (Exception e) {
            this.token = token; // not valid percent-encoded — keep as-is
        }
    }

    /**
     * Brand / concept code (e.g. LIFESTYLE, MAX, BABYSHOP, HOMECENTRE).
     */
    @NotBlank(message = "concept must not be blank")
    private String concept;

    /**
     * Runtime environment prefix (e.g. uat1, uat5, stg, prod).
     * Pass blank / null to target the production URL.
     */
    private String env;

    /**
     * Application identifier sent to the commerce API
     * (e.g. Desktop, Mobile, ANDROID, IPHONE).
     */
    @NotBlank(message = "appId must not be blank")
    private String appId;
}
