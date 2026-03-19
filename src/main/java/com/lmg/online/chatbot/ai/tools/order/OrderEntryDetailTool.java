package com.lmg.online.chatbot.ai.tools.order;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderEntryDetailResponse;
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
 * Spring AI Tool — fetches delivery and return details for a single order entry.
 *
 * <pre>
 *   GET /landmarkshopscommercews/v2/{siteId}/chatbot/user/{userId}/delivery
 *       ?appId={appId}&orderNo={orderNo}&entryPk={entryPk}
 *   Header: access_token: {accessToken}
 * </pre>
 *
 * Returns per-item consignment status (TAT breach, dispatch delay, shipped/delivery
 * dates) and any return already initiated on the item.
 *
 * Always triggered via {@code intentHint = "ORDER_ENTRY_INTENT"} from the widget's
 * "More Details" button — never via free-text classification.
 */
@Slf4j
@Component
public class OrderEntryDetailTool {

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    @Tool(
            name = "getOrderEntryDetail",
            description = """
            Fetches delivery and return details for a specific order entry (item).

            Parameters:
            - userId      (required): Customer's user ID e.g. "918884917698@landmarkmlogindomain.com"
            - accessToken (required): Customer's OAuth access token
            - orderNo     (required): Order number e.g. "9419047049"
            - entryPk     (required): Order entry primary key e.g. "9939862093870"
            - concept     (required): Brand — LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
            - env         (required): Environment — uat1 | uat5 | prod
            - appid       (required): App identifier — ANDROID | IPHONE | Desktop | Mobile

            Returns:
            - exchangeable, returnable flags
            - item_details[]: each with consignment (status, dates, TAT breach flag,
              dispatch-delay flag, return-window-expired flag) and optional returnDetail
              (returnId, rma, returnStatus, returnPickupDelayed, faqLink)
            """
    )
    public OrderEntryDetailResponse getOrderEntryDetail(

            @ToolParam(required = true, description = "Customer's user ID")
            String userId,

            @ToolParam(required = true, description = "Customer's OAuth access token")
            String accessToken,

            @ToolParam(required = true, description = "Order number")
            String orderNo,

            @ToolParam(required = true, description = "Order entry primary key")
            String entryPk,

            @ToolParam(required = true, description = "Brand concept")
            String concept,

            @ToolParam(required = true, description = "Environment")
            String env,

            @ToolParam(required = true, description = "App identifier")
            String appid

    ) {
        log.info("🔍 [OrderEntryDetailTool] userId={}, orderNo={}, entryPk={}, concept={}, env={}",
                userId, orderNo, entryPk, concept, env);

        try {
            // Build URL:  /chatbot/user/{userId}/delivery?appId=...&orderNo=...&entryPk=...
            String uriPath = "/chatbot/user/" + userId + "/delivery";

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("orderNo", orderNo);
            queryParams.put("entryPk", entryPk);

            String url = ConceptBaseUrlResolver.buildApiUrl(concept, env, uriPath, appid, queryParams);
            log.info("🌐 [OrderEntryDetailTool] URL: {}", url);

            // access_token is passed as a request header (not query param) for this endpoint
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("access_token", accessToken);

            OrderEntryDetailResponse response = authenticationServiceUtil
                    .callWithAuthRetry(appid, url, HttpMethod.GET, headers, null,
                            OrderEntryDetailResponse.class, env)
                    .getBody();

            if (response == null) {
                log.warn("⚠️ [OrderEntryDetailTool] Null response for orderNo={}, entryPk={}", orderNo, entryPk);
                return errorResponse(concept);
            }

            log.info("✅ [OrderEntryDetailTool] Retrieved {} item detail(s) for entryPk={}",
                    response.getItemDetails() != null ? response.getItemDetails().size() : 0, entryPk);
            return response;

        } catch (Exception e) {
            log.error("❌ [OrderEntryDetailTool] Error for entryPk={}: {}", entryPk, e.getMessage(), e);
            return errorResponse(concept);
        }
    }

    private OrderEntryDetailResponse errorResponse(String concept) {
        OrderEntryDetailResponse r = new OrderEntryDetailResponse();
        r.setChatMessage("Unable to fetch item details right now. " +
                ConceptBaseUrlResolver.getPhoneNumber(concept));
        return r;
    }
}
