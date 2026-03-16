package com.lmg.online.chatbot.ai.auth;

import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Obtains and caches anonymous OAuth2 access tokens from Hybris.
 *
 * <p>Uses the {@code client_credentials} grant — no user login required.
 * Tokens are cached per {@code concept:env} and refreshed 60 seconds
 * before they expire.  Thread-safe via double-checked locking.</p>
 *
 * <p>Used by {@link com.lmg.online.chatbot.ai.tools.storelocator.StoreLocatorTool}
 * and {@link com.lmg.online.chatbot.ai.tools.giftcard.GiftCardBalanceTool}
 * when no user access-token is available (anonymous visitors).</p>
 *
 * <pre>
 * POST https://{env}.lifestylestores.com/landmarkshopscommercews/in/oauth/token
 * Content-Type: application/x-www-form-urlencoded
 *
 * client_id=mobile_android
 * client_secret=***
 * grant_type=client_credentials
 * appId=Desktop
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymousTokenService {

    // ── OAuth config (application.properties) ────────────────────────────────
    @Value("${hybris.oauth.client-id}")
    private String clientId;

    @Value("${hybris.oauth.client-secret}")
    private String clientSecret;

    @Value("${hybris.oauth.grant-type:client_credentials}")
    private String grantType;

    /** Refresh this many seconds before actual expiry to avoid last-second failures */
    private static final int BUFFER_SECONDS = 60;

    private final RestTemplate restTemplate;

    // ── In-memory token cache: "LIFESTYLE:uat1" → TokenEntry ─────────────────
    private final ConcurrentHashMap<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a valid anonymous access token for the given concept + env.
     *
     * <ul>
     *   <li>Cache hit, not expired → returns immediately (no network call)</li>
     *   <li>Cache miss or expired  → calls Hybris OAuth, caches result</li>
     * </ul>
     *
     * @param concept brand — LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
     * @param env     environment — uat1 | uat5 | prod (null treated as prod)
     * @param appId   app type forwarded to Hybris — Desktop | Mobile | ANDROID | IPHONE
     * @return raw access token string (never null; throws on failure)
     */
    public String getToken(String concept, String env, String appId) {
        String key = cacheKey(concept, env);

        TokenEntry entry = tokenCache.get(key);
        if (entry != null && !entry.isExpired()) {
            log.debug("🔑 [AnonymousToken] Cache hit — concept={}, env={}", concept, env);
            return entry.accessToken;
        }

        return fetchAndCache(key, concept, env, appId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Thread-safe fetch: double-check inside synchronized block. */
    private synchronized String fetchAndCache(String key, String concept, String env, String appId) {
        // Re-check after acquiring lock — another thread may have fetched it already
        TokenEntry existing = tokenCache.get(key);
        if (existing != null && !existing.isExpired()) {
            return existing.accessToken;
        }

        log.info("🔑 [AnonymousToken] Fetching new token — concept={}, env={}", concept, env);

        String url = ConceptBaseUrlResolver.buildTokenUrl(concept, env);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id",     clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type",    grantType);
        body.add("appId",         appId != null ? appId : "Desktop");

        try {
            ResponseEntity<AnonymousTokenResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    AnonymousTokenResponse.class
            );

            AnonymousTokenResponse tokenResponse = response.getBody();
            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                throw new RuntimeException("Empty token response from Hybris OAuth");
            }

            // Cache until (expires_in - BUFFER) seconds from now
            long ttlMs      = (tokenResponse.getExpiresIn() - BUFFER_SECONDS) * 1000L;
            long expiresAt  = System.currentTimeMillis() + ttlMs;
            tokenCache.put(key, new TokenEntry(tokenResponse.getAccessToken(), expiresAt));

            log.info("✅ [AnonymousToken] Token cached for {} — expires_in={}s, refreshes in ~{}s",
                    key, tokenResponse.getExpiresIn(), tokenResponse.getExpiresIn() - BUFFER_SECONDS);

            return tokenResponse.getAccessToken();

        } catch (Exception e) {
            log.error("❌ [AnonymousToken] Failed to fetch token for concept={}, env={}: {}",
                    concept, env, e.getMessage(), e);
            throw new RuntimeException(
                    "Unable to obtain anonymous access token for [" + key + "]: " + e.getMessage(), e);
        }
    }

    private static String cacheKey(String concept, String env) {
        return concept.toUpperCase() + ":" + (env == null || env.isBlank() ? "prod" : env.toLowerCase());
    }

    // ── Inner record — cache entry ────────────────────────────────────────────

    private record TokenEntry(String accessToken, long expiresAtMs) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMs;
        }
    }
}
