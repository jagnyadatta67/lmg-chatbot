package com.lmg.online.chatbot.ai.tools.order;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.order.dto.HybrisSingleOrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fetches full order details for a single order from the standard Hybris order API.
 *
 * <pre>
 *   GET /landmarkshopscommercews/v2/{siteId}/en/users/{userId}/orders/{orderNo}
 *       ?appId={appId}&orderRevamp=true&position=0&fields=DEFAULT
 *
 *   Headers:
 *     Content-Type: application/json
 *     access_token: {accessToken}
 * </pre>
 *
 * Returns a {@link HybrisSingleOrderResponse} which the handler maps into
 * the widget-ready {@code ChatbotOrderTrackingResponse}.
 * Returns {@code null} on error — the handler converts that to a friendly message.
 */
@Slf4j
@Component
public class OrderTrackingTool {

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    /**
     * Fetch full details for a single order.
     *
     * @param userId      Customer's user ID (e.g. phone@landmarkmlogindomain.com)
     * @param accessToken Customer's OAuth access token
     * @param orderNo     Numeric order number e.g. "9419396447"
     * @param concept     Brand: LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
     * @param env         Environment: uat1 | uat5 | prod
     * @param appid       App: ANDROID | IPHONE | Desktop | Mobile
     * @return Deserialized Hybris response, or {@code null} if the call fails
     */
    public HybrisSingleOrderResponse getSingleOrderDetails(
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
            String uriPath = "/en/users/" + userId + "/orders/" + orderNo;

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("orderRevamp", "true");
            queryParams.put("position", "0");
            queryParams.put("fields", "DEFAULT");

            String url = ConceptBaseUrlResolver.buildApiUrl(concept, env, uriPath, appid, queryParams);
            log.info("🌐 [OrderTrackingTool] URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("access_token", accessToken);

            HybrisSingleOrderResponse response = authenticationServiceUtil
                    .callWithAuthRetry(appid, url, HttpMethod.GET, headers, null,
                            HybrisSingleOrderResponse.class, env)
                    .getBody();

            log.info("✅ [OrderTrackingTool] Fetched order={}, status={}",
                    response != null ? response.getCode() : "null",
                    response != null ? response.getStatusDisplay() : "null");

            return response;

        } catch (Exception e) {
            log.error("❌ [OrderTrackingTool] Failed for orderNo={}: {}", orderNo, e.getMessage(), e);
            return null;
        }
    }
}
