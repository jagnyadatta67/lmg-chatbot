package com.lmg.online.chatbot.ai.project.service;



import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.user.dto.UserWsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service that calls the commerce platform's v3 profile endpoint:
 *
 * <pre>
 * GET /landmarkshopscommercews/v3/{siteId}/en/users/current/profile?appId={appId}
 * Header: access_token: {token}
 * </pre>
 *
 * Does NOT go through {@code AuthenticationServiceUtil} OAuth because the caller
 * already supplies the logged-in user's personal {@code access_token}; that token
 * is passed directly as the {@code access_token} header to identify the user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final RestTemplate restTemplate;

    /**
     * Fetch the current logged-in user's profile.
     *
     * @param concept brand code  (e.g. LIFESTYLE, MAX, BABYSHOP, HOMECENTRE)
     * @param env     environment prefix (e.g. uat1, uat5, stg – null/blank = prod)
     * @param token   user's personal access_token obtained after login
     * @param appId   application type  (e.g. Desktop, Mobile, ANDROID, IPHONE)
     * @return {@link UserWsDTO} with uid, firstName, lastName, email, gender, signInMobile
     */
    public UserWsDTO fetchProfile(String concept, String env, String token, String appId) {

        // Build:  https://{env}.{brand-domain}/landmarkshopscommercews/v3/{siteId}/en/users/current/profile?appId={appId}
        String url = ConceptBaseUrlResolver.buildProfileApiUrl(concept, env, appId);
        log.info("[UserProfileService] GET profile → {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", token);          // user's personal session token

        org.springframework.http.HttpEntity<Void> entity =
                new org.springframework.http.HttpEntity<>(null, headers);

        try {
            ResponseEntity<UserWsDTO> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, UserWsDTO.class);

            log.info("[UserProfileService] profile fetched for concept={} env={}", concept, env);
            return response.getBody();

        } catch (Exception ex) {
            log.error("[UserProfileService] Failed to fetch profile: {}", ex.getMessage(), ex);
            throw new RuntimeException("Error fetching user profile: " + ex.getMessage(), ex);
        }
    }
}
