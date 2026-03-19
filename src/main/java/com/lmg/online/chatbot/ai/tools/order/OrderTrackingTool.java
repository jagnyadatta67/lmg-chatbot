package com.lmg.online.chatbot.ai.tools.order;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.order.dto.ChatbotOrderTrackingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fetches order details for a single order from the chatbot order API.
 *
 * <pre>
 *   GET /landmarkshopscommercews/v2/{siteId}/chatbot/user/{userId}/order
 *       ?appId={appId}&orderNo={orderNo}
 *
 *   Headers:
 *     Content-Type: application/json
 *     access_token: {accessToken}
 * </pre>
 *
 * Returns a typed {@link ChatbotOrderTrackingResponse} so the handler can use
 * it directly without any AI involvement or mapper step.
 * Returns {@code null} on error — the handler converts that to a friendly message.
 */
@Slf4j
@Component
public class OrderTrackingTool {

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    /**
     * Fetch details for a single order.
     *
     * @param userId      Customer's user ID (e.g. phone@landmarkmlogindomain.com)
     * @param accessToken Customer's OAuth access token
     * @param orderNo     Numeric order number e.g. "9419396447"
     * @param concept     Brand: LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
     * @param env         Environment: uat1 | uat5 | prod
     * @param appid       App: ANDROID | IPHONE | Desktop | Mobile
     * @return Deserialized response, or {@code null} if the call fails
     */
    public ChatbotOrderTrackingResponse getSingleOrderDetails(
            String userId,
            String accessToken,
            String orderNo,
            String concept,
            String env,
            String appid
    ) {
        log.info("🔍 [OrderTrackingTool] orderNo={}, userId={}, concept={}, env={}",
                orderNo, userId, concept, env);

        try {
            String uriPath = "/chatbot/user/" + userId + "/order";

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("orderNo", orderNo);

            String url = ConceptBaseUrlResolver.buildApiUrl(concept, env, uriPath, appid, queryParams);
            log.info("🌐 [OrderTrackingTool] URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("access_token", accessToken);

            ChatbotOrderTrackingResponse response = authenticationServiceUtil
                    .callWithAuthRetry(appid, url, HttpMethod.GET, headers, null,
                            ChatbotOrderTrackingResponse.class, env)
                    .getBody();

            log.info("✅ [OrderTrackingTool] Fetched {} order(s) for orderNo={}",
                    response != null && response.getOrders() != null ? response.getOrders().size() : 0,
                    orderNo);

            return response;

        } catch (Exception e) {
            log.error("❌ [OrderTrackingTool] Failed for orderNo={}: {}", orderNo, e.getMessage(), e);
            return null;
        }
    }
}
