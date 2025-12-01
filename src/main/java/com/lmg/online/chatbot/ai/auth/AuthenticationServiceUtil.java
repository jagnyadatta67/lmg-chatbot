package com.lmg.online.chatbot.ai.auth;

import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AuthenticationServiceUtil {

    private static final String CLIENT_ID = "mobile_android";
    private static final String CLIENT_SECRET = "F7LBBekbehGRQWpROIKJq";
    private static final String GRANT_TYPE = "client_credentials";

    @Autowired
    private RestTemplate restTemplate;

    private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    /**
     * Main method to call your API with automatic 401 recovery
     */
    public <T> ResponseEntity<T> callWithAuthRetry(
            String appId,
            String url,
            HttpMethod method,
            HttpHeaders header,
            Object body,
            Class<T> responseType,
            String env) {

        log.info("Token Fetch for {} {} {}", url, appId, env);
        String token = getOrFetchToken(appId, env);
        header.set("access_token", token);

        try {
            return callApiWithToken(url, method, header, body, responseType);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("⚠️ Received 401, refreshing token and retrying...");
            token = refreshToken(appId, env);
            header.set("access_token", token);
            return callApiWithToken(url, method, header, body, responseType);
        }
    }

    /**
     * Performs the actual API call using current token.
     */
    private <T> ResponseEntity<T> callApiWithToken(
            String url,
            HttpMethod method,
            HttpHeaders headers,
            Object body,
            Class<T> responseType) {

        HttpEntity<Object> requestEntity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, method, requestEntity, responseType);
    }

    /**
     * Get cached token or fetch a new one
     */
    private String getOrFetchToken(String appId, String env) {
        String cacheKey = buildCacheKey(appId, env);
        return tokenCache.computeIfAbsent(cacheKey, key -> fetchToken(appId, env));
    }

    /**
     * Force-refresh token (used when we get a 401)
     */
    private String refreshToken(String appId, String env) {
        String cacheKey = buildCacheKey(appId, env);
        tokenCache.remove(cacheKey);
        return getOrFetchToken(appId, env);
    }

    /**
     * Call OAuth token endpoint
     * This method should NOT modify the tokenCache directly.
     * The caching is handled by computeIfAbsent in getOrFetchToken.
     */
    private String fetchToken(String appId, String env) {
        log.info("🔑 Fetching new token for appId={}, env={}", appId, env);
        String tokenUrl = ConceptBaseUrlResolver.buildTokenUrl("MAX", env);
        log.info("🌐 Token URL resolved: {}", tokenUrl);


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("appId", appId);
        formData.add("client_id", CLIENT_ID);
        formData.add("client_secret", CLIENT_SECRET);
        formData.add("grant_type", GRANT_TYPE);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formData, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String token = (String) response.getBody().get("access_token");
                if (token != null) {
                    log.info("✅ Token fetched successfully for appId={}, env={}", appId, env);
                    // Don't put into cache here - computeIfAbsent handles it
                    return token;
                }
            }

            throw new RuntimeException("Failed to get access token, response: " + response.getBody());

        } catch (HttpClientErrorException e) {
            log.error("❌ Token fetch failed: {}", e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * Build consistent cache key from appId and env
     */
    private String buildCacheKey(String appId, String env) {
        return appId + ":" + env;
    }
}