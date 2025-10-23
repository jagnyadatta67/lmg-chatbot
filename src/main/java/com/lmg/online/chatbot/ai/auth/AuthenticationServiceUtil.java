package com.lmg.online.chatbot.ai.auth;

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
public class AuthenticationServiceUtil { private static final String TOKEN_URL = "https://uat5.maxfashion.in/landmarkshopscommercews/in/oauth/token";
    private static final String CLIENT_ID = "mobile_android";
    private static final String CLIENT_SECRET = "F7LBBekbehGRQWpROIKJq";
    private static final String GRANT_TYPE = "client_credentials";
@Autowired
    private  RestTemplate restTemplate;
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
            Class<T> responseType,String env) {

log.info(" Token Fetch for {} {} {}",url,appId,env);
        String token = getOrFetchToken(appId,env);
        header.set("access_token",token);
        try {
            return callApiWithToken(url, method, header, body,responseType);
        } catch (HttpClientErrorException.Unauthorized e) {
            System.out.println("⚠️ Received 401, refreshing token and retrying...");
            token = refreshToken(appId,env);
            header.set("access_token",token);
            return callApiWithToken(url, method, header,body , responseType);
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
    private String getOrFetchToken(String appId,String env) {
        return tokenCache.computeIfAbsent(appId, key -> fetchToken(key, env));
    }

    /**
     * Force-refresh token (used when we get a 401)
     */
    private String refreshToken(String appId,String env) {
        tokenCache.remove(appId);
        return fetchToken(appId,env);
    }

    /**
     * Call OAuth token endpoint
     */
    private String fetchToken(String appId,String env) {
        System.out.println("🔑 Fetching new token for appId=" + appId);

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
                    TOKEN_URL,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String token = (String) response.getBody().get("access_token");
                if (token != null) {
                    tokenCache.put(appId+env, token);
                    System.out.println("✅ Token fetched and cached for " + appId);
                    return token;
                }
            }
            throw new RuntimeException("Failed to get access token, response: " + response.getBody());

        } catch (HttpClientErrorException e) {
            System.err.println("❌ Token fetch failed: " + e.getResponseBodyAsString());
            throw e;
        }
    }
}
