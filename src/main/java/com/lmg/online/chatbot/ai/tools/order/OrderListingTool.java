package com.lmg.online.chatbot.ai.tools.order;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderListResponse;
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
import java.util.UUID;

/**
 * Spring AI Tool — fetches a paginated order history list for an authenticated customer.
 *
 * <p>Calls the Hybris Commerce v2 endpoint:
 * <pre>
 *   GET /landmarkshopscommercews/v2/{siteId}/en/users/{userId}/v2/orders
 *       ?access_token=&appId=&pageSize=&orderRefineCode=&currentPage=
 * </pre>
 *
 * <p>Based on the reference cURL:
 * <pre>
 *   GET https://uat1.lifestylestores.com/landmarkshopscommercews/v2/lifestylein
 *       /en/users/918105828495@landmarkmlogindomain.com/v2/orders
 *       ?access_token=a64f3170-...&appId=ANDROID&pageSize=10&orderRefineCode=2&currentPage=1
 * </pre>
 */
@Slf4j
@Component
public class OrderListingTool {

    /** Default page size — matches the reference cURL */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** orderRefineCode values understood by the Hybris backend */
    public static final String REFINE_ALL        = "0";   // all orders
    public static final String REFINE_ACTIVE     = "1";   // in-progress / active
    public static final String REFINE_COMPLETED  = "2";   // delivered / completed
    public static final String REFINE_CANCELLED  = "3";   // cancelled

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    @Tool(
            name = "getOrderList",
            description = """
            Fetches a paginated list of orders for an authenticated customer.

            Parameters:
            - userId      (required): Customer's user ID e.g. "918884917698@landmarkmlogindomain.com"
            - accessToken (required): Customer's OAuth access token (UUID format)
            - concept     (required): Brand concept — LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
            - env         (required): Environment — uat1 | uat5 | prod
            - appid       (required): App identifier — ANDROID | IPHONE | Desktop | Mobile
            - pageSize    (optional): Orders per page, default 10
            - currentPage (optional): Page number (1-indexed), default 1
            - orderRefineCode (optional): Filter code —
                "0" = All orders (default)
                "1" = Active / in-progress
                "2" = Completed / delivered
                "3" = Cancelled

            Returns:
            Structured order list containing:
            - List of orders with order code, status, placed date, total amount, item count
            - Pagination info (current page, total pages, total results)
            - chat_message field set on error or empty list

            Usage examples:
            getOrderList("918884917698@landmarkmlogindomain.com", "a64f3170-...", "LIFESTYLE", "uat1", "ANDROID")
            getOrderList("918884917698@landmarkmlogindomain.com", "a64f3170-...", "MAX", "prod", "Desktop", 5, 1, "2")
            """
    )
    public OrderListResponse getOrderList(

            @ToolParam(required = true,
                    description = "Customer's user ID (phone@landmarkmlogindomain.com)")
            String userId,

            @ToolParam(required = true,
                    description = "OAuth access token for the authenticated customer")
            String accessToken,

            @ToolParam(required = true,
                    description = "Brand concept: LIFESTYLE | MAX | HOMECENTRE | BABYSHOP")
            String concept,

            @ToolParam(required = true,
                    description = "Target environment: uat1 | uat5 | prod")
            String env,

            @ToolParam(required = true,
                    description = "App identifier: ANDROID | IPHONE | Desktop | Mobile")
            String appid,

            @ToolParam(required = false,
                    description = "Orders per page (default: 10)")
            Integer pageSize,

            @ToolParam(required = false,
                    description = "Page number, 1-indexed (default: 1)")
            Integer currentPage,

            @ToolParam(required = false,
                    description = "Filter: 0=All, 1=Active, 2=Completed, 3=Cancelled (default: 0)")
            String orderRefineCode

    ) {
        log.info("📋 [OrderListingTool] userId={}, concept={}, env={}, appid={}, page={}, pageSize={}, refineCode={}",
                userId, concept, env, appid, currentPage, pageSize, orderRefineCode);

        // ── Defaults ───────────────────────────────────────────────────────────
        int size     = (pageSize    != null && pageSize    > 0) ? pageSize    : DEFAULT_PAGE_SIZE;
        int page     = (currentPage != null && currentPage > 0) ? currentPage : 1;
        String refine = (orderRefineCode != null && !orderRefineCode.isBlank()) ? orderRefineCode : REFINE_ALL;

        try {
            // ── Build URL ──────────────────────────────────────────────────────
            // Path: /en/users/{userId}/v2/orders
            // ConceptBaseUrlResolver adds: /landmarkshopscommercews/v2/{siteId}/
            String uriPath = "/en/users/" + userId + "/v2/orders";

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("access_token",    accessToken);
            queryParams.put("pageSize",        String.valueOf(size));
            queryParams.put("orderRefineCode", refine);
            queryParams.put("currentPage",     String.valueOf(page));

            String url = ConceptBaseUrlResolver.buildApiUrl(concept, env, uriPath, appid, queryParams);
            log.info("🌐 [OrderListingTool] Calling: {}", url);

            // ── Build headers ──────────────────────────────────────────────────
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("access_token",  accessToken);
            headers.set("X-Trace-ID",    UUID.randomUUID().toString() + "-A");

            // ── Execute ────────────────────────────────────────────────────────
            OrderListResponse response = authenticationServiceUtil
                    .callWithAuthRetry(appid, url, HttpMethod.GET, headers, null, OrderListResponse.class, env)
                    .getBody();

            if (response == null) {
                log.warn("⚠️ [OrderListingTool] Null response body for userId={}", userId);
                return emptyResponse(concept);
            }

            // Friendly fallback when list is genuinely empty
            if (response.getOrders() == null || response.getOrders().isEmpty()) {
                log.info("ℹ️ [OrderListingTool] No orders found for userId={}, refineCode={}", userId, refine);
                response.setChatMessage(buildEmptyMessage(refine, concept));
            }

            log.info("✅ [OrderListingTool] Found {} orders for userId={}",
                    response.getOrders() != null ? response.getOrders().size() : 0, userId);
            return response;

        } catch (Exception e) {
            log.error("❌ [OrderListingTool] Error fetching orders for userId={}, concept={}: {}",
                    userId, concept, e.getMessage(), e);
            return errorResponse(concept);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private OrderListResponse emptyResponse(String concept) {
        OrderListResponse r = new OrderListResponse();
        r.setChatMessage(buildEmptyMessage(REFINE_ALL, concept));
        return r;
    }

    private OrderListResponse errorResponse(String concept) {
        OrderListResponse r = new OrderListResponse();
        r.setChatMessage(String.format(
                "We're currently unable to retrieve your order history. Please try again shortly. %s",
                ConceptBaseUrlResolver.getPhoneNumber(concept)
        ));
        return r;
    }

    private String buildEmptyMessage(String refineCode, String concept) {
        return switch (refineCode) {
            case REFINE_ACTIVE    -> "You have no active orders at the moment. Start shopping at " + concept + "! 🛍️";
            case REFINE_COMPLETED -> "No completed orders found. Your delivered orders will appear here.";
            case REFINE_CANCELLED -> "No cancelled orders found.";
            default               -> "No orders found for your account. Start shopping at " + concept + "! 🛍️";
        };
    }
}
