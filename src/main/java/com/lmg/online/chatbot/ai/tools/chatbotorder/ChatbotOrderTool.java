package com.lmg.online.chatbot.ai.tools.chatbotorder;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.chatbotorder.dto.ChatbotOrderListResponse;
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
 * Spring AI Tool — fetches the chatbot-specific order list for a customer.
 *
 * <pre>
 *   GET /landmarkshopscommercews/v2/{siteId}/chatbot/user/{customerId}/order
 *       ?appId={appId}&access_token={token}&noOfOrders={count}
 * </pre>
 * access_token is passed as a query parameter — Hybris OAuth filter reads it
 * from the URL, NOT from a custom header.
 */
@Slf4j
@Component
public class ChatbotOrderTool {

    private static final int DEFAULT_ORDER_COUNT = 5;

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    @Tool(
            name = "getChatbotOrderList",
            description = """
            Fetches recent orders for an authenticated customer using the chatbot order API.

            Parameters:
            - customerId  (required): Customer's user ID e.g. "918884917698@landmarkmlogindomain.com"
            - concept     (required): Brand — LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
            - env         (required): Environment — uat1 | uat2 | uat5 | prod
            - appid       (required): App identifier — ANDROID | IPHONE | Desktop | Mobile
            - accessToken (required): Customer's OAuth access token
            - noOfOrders  (optional): Number of orders to return, default 10

            Returns:
            - customerId, list of orders each with orderNo, orderStatus, orderDate,
              orderAmount, numberOfShipments, numberOfProductsDelivered, entries[]
            - Each entry has orderEntryPk, productCode, productImage, productUrl,
              quantity, exchangeable, returnable
            - chat_message set on error or empty list
            """
    )
    public ChatbotOrderListResponse getChatbotOrderList(

            @ToolParam(required = true, description = "Customer's user ID (phone@landmarkmlogindomain.com)")
            String customerId,

            @ToolParam(required = true, description = "Brand: LIFESTYLE | MAX | HOMECENTRE | BABYSHOP")
            String concept,

            @ToolParam(required = true, description = "Environment: uat1 | uat2 | uat5 | prod")
            String env,

            @ToolParam(required = true, description = "App: ANDROID | IPHONE | Desktop | Mobile")
            String appid,

            @ToolParam(required = true, description = "Customer's OAuth access token")
            String accessToken,

            @ToolParam(required = false, description = "Number of orders to fetch (default: 10)")
            Integer noOfOrders

    ) {
        int count = (noOfOrders != null && noOfOrders > 0) ? noOfOrders : DEFAULT_ORDER_COUNT;
        log.info("📋 [ChatbotOrderTool] customerId={}, concept={}, env={}, count={}", customerId, concept, env, count);

        try {
            String uriPath = "/chatbot/user/" + customerId + "/order";

            // access_token is a query param — Hybris OAuth filter reads it from the URL
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("access_token", accessToken);
            queryParams.put("noOfOrders", String.valueOf(count));  // Hybris param name is noOfOrders

            String url = ConceptBaseUrlResolver.buildApiUrl(concept, env, uriPath, appid, queryParams);
            log.info("🌐 [ChatbotOrderTool] URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ChatbotOrderListResponse response = authenticationServiceUtil
                    .callWithAuthRetry(appid, url, HttpMethod.GET, headers, null, ChatbotOrderListResponse.class, env)
                    .getBody();

            if (response == null) {
                return errorResponse(concept);
            }

            if (response.getOrders() == null || response.getOrders().isEmpty()) {
                response.setChatMessage("You have no recent orders. Start shopping at " + concept + "! 🛍️");
            }

            log.info("✅ [ChatbotOrderTool] Retrieved {} orders", response.getOrders() != null ? response.getOrders().size() : 0);
            return response;

        } catch (Exception e) {
            log.error("❌ [ChatbotOrderTool] Error: {}", e.getMessage(), e);
            return errorResponse(concept);
        }
    }

    private ChatbotOrderListResponse errorResponse(String concept) {
        ChatbotOrderListResponse r = new ChatbotOrderListResponse();
        r.setChatMessage("Unable to fetch your orders right now. " +
                ConceptBaseUrlResolver.getPhoneNumber(concept));
        return r;
    }
}
