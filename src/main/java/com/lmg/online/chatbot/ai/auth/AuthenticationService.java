package com.lmg.online.chatbot.ai.auth;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthenticationService {
    @Autowired
    private RestTemplate restTemplate;

    /**
     * Fetch OAuth token for a given appId.
     */
    public String getAccessToken(String appId, String concept, String env) {

        // --- Prepare headers ---
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // --- Prepare request body ---
        // ✅ Proper form body
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("appId", appId);
        headers.set("client_secret", "F7LBBekbehGRQWpROIKJq");
        headers.set("grant_type","client_credentials");
        headers.set("client_id", "mobile_android");
        String tokenUrl = com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver.buildAuthUrl(concept, env) + "/landmarkshopscommercews/in/oauth/token";

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formData, headers);

        ResponseEntity<String> response = restTemplate.exchange(tokenUrl, HttpMethod.POST, entity, String.class);

           return response.getBody();

    }


    public String getAccessToken(String appId) {
        String tokenUrl = "https://uat5.maxfashion.in/landmarkshopscommercews/in/oauth/token";
       // RestTemplate restTemplate = SSLUtil.getInsecureRestTemplate(); // If SSL self-signed

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("appId", appId);
        formData.add("client_id", "mobile_android");
        formData.add("client_secret", "F7LBBekbehGRQWpROIKJq");
        formData.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formData, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object token = response.getBody().get("access_token");
                if (token != null) {
                    System.out.println("✅ Access Token: " + token);
                    return token.toString();
                }
            }
            throw new RuntimeException("Token not found in response: " + response.getBody());

        } catch (HttpClientErrorException e) {
            System.err.println("❌ Auth failed: " + e.getStatusCode());
            System.err.println("Response: " + e.getResponseBodyAsString());
            throw new RuntimeException("Unauthorized: " + e.getMessage(), e);
        }
    }
}