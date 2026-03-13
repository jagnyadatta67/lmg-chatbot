package com.lmg.online.chatbot.ai.tools.user;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.user.dto.UserWsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Fetches the logged-in customer's profile from Hybris.
 *
 * <pre>
 *   GET /landmarkshopscommercews/v3/{siteId}/en/users/current/profile
 *       ?appId={appId}
 *
 *   Headers:
 *     Content-Type: application/json
 *     access_token: {accessToken}
 * </pre>
 *
 * Returns {@link UserWsDTO} on success, or a DTO with {@code chat_message}
 * set to a friendly error string on failure — never throws.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MyProfileDetailsTool {

    private final AuthenticationServiceUtil authenticationServiceUtil;

    /**
     * @param userId      Customer's user ID (phone@landmarkmlogindomain.com)
     * @param accessToken Customer's OAuth access token
     * @param concept     Brand: LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
     * @param env         Environment: uat1 | uat5 | prod
     * @param appId       App: ANDROID | IPHONE | Desktop | Mobile
     * @return Populated {@link UserWsDTO}, never {@code null}
     */
    public UserWsDTO getProfile(String userId,
                                String accessToken,
                                String concept,
                                String env,
                                String appId) {

        log.info("👤 [MyProfileDetailsTool] userId={}, concept={}, env={}", userId, concept, env);

        try {
            String url = ConceptBaseUrlResolver.buildProfileApiUrl(concept, env, appId);
            log.info("🌐 [MyProfileDetailsTool] URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("access_token", accessToken);

            UserWsDTO response = authenticationServiceUtil
                    .callWithAuthRetry(appId, url, HttpMethod.GET, headers, null, UserWsDTO.class, env)
                    .getBody();

            if (response == null) {
                log.warn("⚠️ [MyProfileDetailsTool] null response for userId={}", userId);
                return errorResponse(concept);
            }

            log.info("✅ [MyProfileDetailsTool] Fetched profile for uid={}", response.getUid());
            return response;

        } catch (Exception e) {
            log.error("❌ [MyProfileDetailsTool] Error for userId={}: {}", userId, e.getMessage(), e);
            return errorResponse(concept);
        }
    }

    private UserWsDTO errorResponse(String concept) {
        UserWsDTO err = new UserWsDTO();
        err.setChat_message("Unable to fetch your profile right now. "
                + ConceptBaseUrlResolver.getPhoneNumber(concept));
        return err;
    }
}
