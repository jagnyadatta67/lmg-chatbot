package com.lmg.online.chatbot.ai.tools.wallet;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.wallet.dto.WalletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring AI Tool — fetches wallet/credit balance for a customer.
 *
 * <pre>
 *   GET /landmarkshopscommercews/v2/{siteId}/chatbot/user/{customerId}/wallet
 *       ?appId={appId}&access_token={token}
 * </pre>
 * access_token is a query param — Hybris OAuth filter reads it from the URL.
 */
@Slf4j
@Component
public class WalletTool {

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    @Tool(
            name = "getWalletBalance",
            description = """
            Fetches the wallet/store credit balance for an authenticated customer.
            Use this when the customer asks about their wallet balance, store credit,
            available credit, or cashback balance.

            Parameters:
            - customerId  (required): Customer's user ID e.g. "918884917698@landmarkmlogindomain.com"
            - accessToken (required): Customer's OAuth access token
            - concept     (required): Brand — LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
            - env         (required): Environment — uat1 | uat2 | uat5 | prod
            - appid       (required): App identifier — ANDROID | IPHONE | Desktop | Mobile

            Returns:
            - walletAmount: current wallet balance (double, e.g. 250.0)
            - walletLink: relative URL to wallet details page (e.g. "/my-credit/walletDetails")
            - chat_message set on error
            """
    )
    public WalletResponse getWalletBalance(

            @ToolParam(required = true, description = "Customer's user ID")
            String customerId,

            @ToolParam(required = true, description = "Customer's OAuth access token")
            String accessToken,

            @ToolParam(required = true, description = "Brand: LIFESTYLE | MAX | HOMECENTRE | BABYSHOP")
            String concept,

            @ToolParam(required = true, description = "Environment: uat1 | uat2 | uat5 | prod")
            String env,

            @ToolParam(required = true, description = "App: ANDROID | IPHONE | Desktop | Mobile")
            String appid

    ) {
        log.info("💰 [WalletTool] customerId={}, concept={}, env={}", customerId, concept, env);

        try {
            String uriPath = "/chatbot/user/" + customerId + "/wallet";

            // access_token is a query param — Hybris OAuth filter reads it from the URL
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("access_token", accessToken);

            String url = ConceptBaseUrlResolver.buildApiUrl(concept, env, uriPath, appid, queryParams);
            log.info("🌐 [WalletTool] URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            WalletResponse response = authenticationServiceUtil
                    .callWithAuthRetry(appid, url, HttpMethod.GET, headers, null, WalletResponse.class, env)
                    .getBody();

            if (response == null) {
                return errorResponse(concept);
            }

            log.info("✅ [WalletTool] walletAmount={}", response.getWalletAmount());
            return response;

        } catch (Exception e) {
            log.error("❌ [WalletTool] Error: {}", e.getMessage(), e);
            return errorResponse(concept);
        }
    }

    private WalletResponse errorResponse(String concept) {
        WalletResponse r = new WalletResponse();
        r.setChatMessage("Unable to fetch your wallet balance right now. " +
                ConceptBaseUrlResolver.getPhoneNumber(concept));
        return r;
    }
}
