package com.lmg.online.chatbot.ai.tools.delivery;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.delivery.dto.DeliveryResponse;
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
 * Spring AI Tool — fetches delivery/shipment details for a specific order entry.
 *
 * <pre>
 *   GET /landmarkshopscommercews/v2/{siteId}/chatbot/user/{customerId}/delivery
 *       ?appId={appId}&access_token={token}&orderNo={orderNo}&entryPk={entryPk}&productCode={productCode}
 * </pre>
 * access_token is a query param — Hybris OAuth filter reads it from the URL.
 */
@Slf4j
@Component
public class DeliveryTrackingTool {

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    @Tool(
            name = "getDeliveryDetails",
            description = """
            Fetches delivery and shipment details for a specific order item.
            Use this when the customer asks about delivery status, shipment tracking,
            when their order will arrive, or return/exchange status of a specific item.

            Parameters:
            - customerId  (required): Customer's user ID e.g. "918884917698@landmarkmlogindomain.com"
            - accessToken (required): Customer's OAuth access token
            - orderNo     (required): Order number e.g. "3400746000"
            - entryPk     (required): Order entry primary key e.g. "8813066879022"
            - productCode (optional): Product code for the order item e.g. "ABC123"
            - concept     (required): Brand — LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
            - env         (required): Environment — uat1 | uat2 | uat5 | prod
            - appid       (required): App identifier — ANDROID | IPHONE | Desktop | Mobile

            Returns:
            - orderNo, orderEntryPk, productCode, productImageUrl, productUrl
            - exchangeable, returnable flags
            - item_details[]: position, quantity, status, consignment (code, status,
              shippedDate, actualDeliveryDate, deliveryTatBreached, dispatchDelayed,
              returnWindowExpired), returnDetail (returnId, rma, returnStatus,
              returnCreationDate, returnInitiated, faqLink)
            - chat_message set on error
            """
    )
    public DeliveryResponse getDeliveryDetails(

            @ToolParam(required = true, description = "Customer's user ID")
            String customerId,

            @ToolParam(required = true, description = "Customer's OAuth access token")
            String accessToken,

            @ToolParam(required = true, description = "Order number e.g. 3400746000")
            String orderNo,

            @ToolParam(required = true, description = "Order entry primary key e.g. 8813066879022")
            String entryPk,

            @ToolParam(required = false, description = "Product code for the order item e.g. ABC123")
            String productCode,

            @ToolParam(required = true, description = "Brand: LIFESTYLE | MAX | HOMECENTRE | BABYSHOP")
            String concept,

            @ToolParam(required = true, description = "Environment: uat1 | uat2 | uat5 | prod")
            String env,

            @ToolParam(required = true, description = "App: ANDROID | IPHONE | Desktop | Mobile")
            String appid

    ) {
        log.info("🚚 [DeliveryTrackingTool] customerId={}, orderNo={}, entryPk={}, productCode={}, concept={}",
                customerId, orderNo, entryPk, productCode, concept);

        try {
            String uriPath = "/chatbot/user/" + customerId + "/delivery";

            // access_token is a query param — Hybris OAuth filter reads it from the URL
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("access_token", accessToken);
            queryParams.put("orderNo",      orderNo);
            queryParams.put("entryPk",      entryPk);
            if (productCode != null && !productCode.isBlank()) {
                queryParams.put("productCode", productCode);
            }

            String url = ConceptBaseUrlResolver.buildApiUrl(concept, env, uriPath, appid, queryParams);
            log.info("🌐 [DeliveryTrackingTool] URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            DeliveryResponse response = authenticationServiceUtil
                    .callWithAuthRetry(appid, url, HttpMethod.GET, headers, null, DeliveryResponse.class, env)
                    .getBody();

            if (response == null) {
                return errorResponse(concept);
            }

            log.info("✅ [DeliveryTrackingTool] Delivery data fetched for orderNo={}", orderNo);
            return response;

        } catch (Exception e) {
            log.error("❌ [DeliveryTrackingTool] Error orderNo={}: {}", orderNo, e.getMessage(), e);
            return errorResponse(concept);
        }
    }

    private DeliveryResponse errorResponse(String concept) {
        DeliveryResponse r = new DeliveryResponse();
        r.setChatMessage("Unable to fetch delivery details right now. " +
                ConceptBaseUrlResolver.getPhoneNumber(concept));
        return r;
    }
}
