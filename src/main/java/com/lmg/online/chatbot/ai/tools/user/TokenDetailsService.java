package com.lmg.online.chatbot.ai.tools.user;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.user.dto.UserWsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;


/**
 * Service that resolves a commerce-platform token to a {@link UserWsDTO}.
 *
 * <p>Delegates the HTTP call to the external endpoint:
 * <pre>
 *   POST {baseUrl}/landmarkshopscommercews/v2/{siteId}/chatbot/getTokenDetails
 *        ?token={token}&appId={appId}
 * </pre>
 * URL construction is handled by {@link ConceptBaseUrlResolver} so the
 * correct base domain and siteId are picked automatically for every concept
 * (LIFESTYLE, MAX, BABYSHOP, HOMECENTRE) and environment (uat1, prod …).
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenDetailsService {

    private final AuthenticationServiceUtil authenticationServiceUtil;
    private final RestTemplate restTemplate;

    /**
     * Exchanges a user {@code token} for a populated {@link UserWsDTO}.
     *
     * @param concept brand code  – LIFESTYLE / MAX / BABYSHOP / HOMECENTRE
     * @param env     environment prefix – uat1, uat5, stg, prod (null = prod)
     * @param token   encrypted user token issued by the commerce platform
     * @param appId   client app type – Desktop / Mobile / ANDROID / IPHONE
     * @return {@link UserWsDTO} populated with the user's profile data
     */
    public UserWsDTO getTokenDetails(String concept, String env, String token, String appId) {

        // Build: {baseUrl}/landmarkshopscommercews/v2/{siteId}/chatbot/getTokenDetails
        //        ?appId={appId}&token={token}
        // Uses buildTokenDetailsUrl (not buildApiUrl) to guarantee the token is
        // encoded exactly once — even if it arrives already percent-encoded.
        String url = ConceptBaseUrlResolver.buildTokenDetailsUrl(concept, env, token, appId);

        log.info("🔑 getTokenDetails → concept={}, env={}, appId={}", concept, env, appId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            // Use URI-based call to prevent RestTemplate from re-encoding
            // already percent-encoded characters in the token (%3D → %253D).
            URI uri = ConceptBaseUrlResolver.buildTokenDetailsUri(concept, env, token, appId);
            log.info("🌐 Token details URI → {}", uri);

            UserWsDTO userWsDTO = restTemplate
                    .exchange(uri, HttpMethod.POST, new HttpEntity<>(null, headers), UserWsDTO.class)
                    .getBody();

            log.info("✅ getTokenDetails succeeded – uid={}", userWsDTO != null ? userWsDTO.getUid() : "null");
            return userWsDTO;

        } catch (Exception e) {
            log.error("❌ getTokenDetails failed for concept={}, env={}: {}", concept, env, e.getMessage(), e);
            throw new RuntimeException("Error fetching token details: " + e.getMessage(), e);
        }
    }
}
