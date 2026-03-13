package com.lmg.online.chatbot.ai.tools.returnstatus;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.returnstatus.dto.ReturnStatusResponse;
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
 * Spring AI Tool — fetches return/refund status for a specific order and RMA.
 *
 * <pre>
 *   GET /landmarkshopscommercews/v2/{siteId}/chatbot/user/{customerId}/return
 *       ?appId={appId}&access_token={token}&orderNo={orderNo}&rmaNo={rmaNo}
 * </pre>
 * access_token is a query param — Hybris OAuth filter reads it from the URL.
 */
@Slf4j
@Component
public class ReturnStatusTool {

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    @Tool(
            name = "getReturnStatus",
            description = """
            Fetches return and refund status for a specific order return request.
            Use this when the customer asks about their return, refund, RMA status,
            pickup status, or refund amount for a returned item.

            Parameters:
            - customerId  (required): Customer's user ID e.g. "918884917698@landmarkmlogindomain.com"
            - accessToken (required): Customer's OAuth access token
            - orderNo     (required): Order number e.g. "3400746000"
            - rmaNo       (required): Return Merchandise Authorization number e.g. "10221001"
            - concept     (required): Brand — LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
            - env         (required): Environment — uat1 | uat2 | uat5 | prod
            - appid       (required): App identifier — ANDROID | IPHONE | Desktop | Mobile

            Returns:
            - consignmentCode, returnId, rma, returnStatus (e.g. REFUND_PROCESSED, WAIT),
              returnCreationDate, returnInitiated, returnPickupDelayed,
              totalRefundAmount, faqLink
            - chat_message set on error
            """
    )
    public ReturnStatusResponse getReturnStatus(

            @ToolParam(required = true, description = "Customer's user ID")
            String customerId,

            @ToolParam(required = true, description = "Customer's OAuth access token")
            String accessToken,

            @ToolParam(required = true, description = "Order number e.g. 3400746000")
            String orderNo,

            @ToolParam(required = true, description = "RMA (Return Merchandise Authorization) number")
            String rmaNo,

            @ToolParam(required = true, description = "Brand: LIFESTYLE | MAX | HOMECENTRE | BABYSHOP")
            String concept,

            @ToolParam(required = true, description = "Environment: uat1 | uat2 | uat5 | prod")
            String env,

            @ToolParam(required = true, description = "App: ANDROID | IPHONE | Desktop | Mobile")
            String appid

    ) {
        log.info("↩️ [ReturnStatusTool] customerId={}, orderNo={}, rmaNo={}", customerId, orderNo, rmaNo);

        try {
            String uriPath = "/chatbot/user/" + customerId + "/return";

            // access_token is a query param — Hybris OAuth filter reads it from the URL
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("access_token", accessToken);
            queryParams.put("orderNo",       orderNo);
            queryParams.put("rmaNo",         rmaNo);

            String url = ConceptBaseUrlResolver.buildApiUrl(concept, env, uriPath, appid, queryParams);
            log.info("🌐 [ReturnStatusTool] URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ReturnStatusResponse response = authenticationServiceUtil
                    .callWithAuthRetry(appid, url, HttpMethod.GET, headers, null, ReturnStatusResponse.class, env)
                    .getBody();

            if (response == null) {
                return errorResponse(concept);
            }

            log.info("✅ [ReturnStatusTool] returnStatus={}", response.getReturnStatus());
            return response;

        } catch (Exception e) {
            log.error("❌ [ReturnStatusTool] Error: {}", e.getMessage(), e);
            return errorResponse(concept);
        }
    }

    private ReturnStatusResponse errorResponse(String concept) {
        ReturnStatusResponse r = new ReturnStatusResponse();
        r.setChatMessage("Unable to fetch return status right now. " +
                ConceptBaseUrlResolver.getPhoneNumber(concept));
        return r;
    }
}
