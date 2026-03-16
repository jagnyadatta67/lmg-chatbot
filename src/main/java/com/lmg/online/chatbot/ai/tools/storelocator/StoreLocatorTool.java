package com.lmg.online.chatbot.ai.tools.storelocator;

import com.lmg.online.chatbot.ai.auth.AnonymousTokenService;
import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.storelocator.dto.StoreList;
import com.lmg.online.chatbot.ai.tools.storelocator.dto.StoreLocatorAPIResponse;
import com.lmg.online.chatbot.ai.tools.storelocator.helper.StoreLocatorHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StoreLocatorTool {

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    @Autowired
    private AnonymousTokenService anonymousTokenService;

    @Tool(
            name = "fetchStoreLocator",
            description = """
            Fetches nearby store locations for a given concept and environment.
            Uses Landmark's Store Locator API to return details like store ID, name,
            city, address, and contact number.

            Parameters:
            - concept     (required): Brand — LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
            - env         (required): Environment — uat1 | uat5 | prod
            - userId      (optional): Customer user ID (not used for auth here)
            - lat         (required): User latitude
            - lng         (required): User longitude
            - appId       (required): App identifier — ANDROID | IPHONE | Desktop | Mobile
            - accessToken (optional): User's OAuth access token; anonymous token used if blank

            Returns: A list of nearest StoreView entries
            """
    )
    public StoreList fetchStoreLocator(
            String concept,
            String env,
            String userId,
            double lat,
            double lng,
            String appId,
            String accessToken) {

        log.info("🏪 [StoreLocatorTool] concept={}, env={}, lat={}, lng={}", concept, env, lat, lng);

        try {
            // Resolve token: use user's token if present, otherwise fetch anonymous token
            String tokenToUse = (accessToken != null && !accessToken.isBlank())
                    ? accessToken
                    : anonymousTokenService.getToken(concept, env, appId);

            String url = ConceptBaseUrlResolver.buildApiUrl(concept, env, "/en/storeLocator/", appId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("access_token", tokenToUse);

            StoreLocatorAPIResponse res = authenticationServiceUtil
                    .callWithAuthRetry(appId, url, HttpMethod.POST, headers, null,
                            StoreLocatorAPIResponse.class, env)
                    .getBody();

            return mapStores(res, lat, lng);

        } catch (Exception e) {
            log.error("❌ [StoreLocatorTool] Error: {}", e.getMessage(), e);
            StoreList errorResult = new StoreList();
            errorResult.setChatMessage("Unable to find nearby stores right now. "
                    + ConceptBaseUrlResolver.getPhoneNumber(concept));
            return errorResult;
        }
    }

    public static StoreList mapStores(StoreLocatorAPIResponse response, double lat, double lng) {
        return StoreLocatorHelper.getNearestStores(response, lat, lng, 10);
    }
}
