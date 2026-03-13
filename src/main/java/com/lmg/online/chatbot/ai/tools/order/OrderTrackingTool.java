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
import java.util.UUID;

/**
 * Fetches full details for a single order from Hybris.
 *
 * <pre>
 *   GET /landmarkshopscommercews/v2/{siteId}/en/users/{userId}/orders/{orderNo}
 *       ?orderRevamp=true&appId={appId}&position=0&fields=DEFAULT
 *
 *   Headers:
 *     Content-Type: application/json
 *     access_token: {accessToken}          ← Hybris reads token from header
 *     X-Trace-ID:   {uuid}-A
 * </pre>
 *
 * Returns a typed {@link HybrisSingleOrderResponse} so the handler can map
 * it directly to {@code OrderResponse} without any AI involvement.
 * Returns {@code null} on error — the handler converts that to a friendly message.
 */
@Slf4j
@Component
public class OrderTrackingTool {

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    /**
     * Fetch a single order's details.
     *
     * @param userId      Customer's user ID (phone@landmarkmlogindomain.com)
     * @param accessToken Customer's OAuth access token
     * @param orderNo     Numeric order number e.g. "9419396447"
     * @param concept     Brand: LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
     * @param env         Environment: uat1 | uat5 | prod
     * @param appid       App: ANDROID | IPHONE | Desktop | Mobile
     * @return Deserialized order, or {@code null} if the call fails
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
            // /en/users/{userId}/orders/{orderNo}
            String uriPath = "/en/users/" + userId + "/orders/" + orderNo;

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("orderRevamp", "true");
            queryParams.put("position",    "0");
            queryParams.put("fields",      "DEFAULT");

            String url = ConceptBaseUrlResolver.buildApiUrl(concept, env, uriPath, appid, queryParams);
            log.info("🌐 [OrderTrackingTool] URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("access_token", accessToken);
            headers.set("X-Trace-ID",   UUID.randomUUID().toString() + "-A");

            HybrisSingleOrderResponse response = authenticationServiceUtil
                    .callWithAuthRetry(appid, url, HttpMethod.GET, headers, null,
                            HybrisSingleOrderResponse.class, env)
                    .getBody();

            log.info("✅ [OrderTrackingTool] Fetched order code={} status={}",
                    response != null ? response.getCode() : "null",
                    response != null ? response.getStatusDisplay() : "null");

            return response;

        } catch (Exception e) {
            log.error("❌ [OrderTrackingTool] Failed for orderNo={}: {}", orderNo, e.getMessage(), e);
            return null;  // handler converts null → friendly error message
        }
    }
}
