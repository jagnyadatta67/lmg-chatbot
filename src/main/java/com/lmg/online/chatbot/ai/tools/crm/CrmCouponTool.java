package com.lmg.online.chatbot.ai.tools.crm;

import com.lmg.online.chatbot.ai.tools.crm.dto.CrmCouponResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calls the LMG CRM API to fetch the coupon list for a given mobile number and brand.
 *
 * <pre>
 *   POST {app.crm.base-url}/MaxCouponUp/LMRService/GetCouponDetails
 *
 *   Headers:
 *     AUTHKEY: {app.crm.auth-key}
 *     Content-Type: application/json
 *
 *   Body:
 *     { "mobileNumber": "...", "ouCode": "..." }
 * </pre>
 *
 * ouCode mapping:
 *   LIFESTYLE  → LS
 *   MAX        → MAX
 *   HOMECENTRE → HC
 *   BABYSHOP   → BS
 */
@Slf4j
@Component
public class CrmCouponTool {

    private final RestTemplate restTemplate;
    private final String crmBaseUrl;
    private final String crmAuthKey;

    private static final String COUPON_PATH = "/MaxCouponUp/LMRService/GetCouponDetails";

    public CrmCouponTool(RestTemplate restTemplate,
                         @Value("${app.crm.base-url}") String crmBaseUrl,
                         @Value("${app.crm.auth-key}") String crmAuthKey) {
        this.restTemplate = restTemplate;
        this.crmBaseUrl   = crmBaseUrl;
        this.crmAuthKey   = crmAuthKey;
    }

    /**
     * Fetches the coupon list from CRM for the given mobile number and brand concept.
     *
     * @param mobileNumber 10-digit mobile number (from user profile signInMobile)
     * @param concept      Brand: LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
     * @return {@link CrmCouponResponse}, never {@code null}
     */
    public CrmCouponResponse getCoupons(String mobileNumber, String concept) {
        log.info("🎟️ [CrmCouponTool] mobileNumber={}, concept={}", mobileNumber, concept);

        try {
            String ouCode = resolveOuCode(concept);
            String url    = crmBaseUrl + COUPON_PATH;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("AUTHKEY", crmAuthKey);

            Map<String, String> body = new LinkedHashMap<>();
            body.put("mobileNumber", mobileNumber);
            body.put("ouCode", ouCode);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            log.info("🌐 [CrmCouponTool] POST {} | ouCode={}", url, ouCode);

            ResponseEntity<CrmCouponResponse> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, CrmCouponResponse.class);

            CrmCouponResponse result = response.getBody();

            if (result == null) {
                log.warn("⚠️ [CrmCouponTool] Null response for mobileNumber={}", mobileNumber);
                return errorResponse();
            }

            log.info("✅ [CrmCouponTool] couponCount={}, resultCode={}",
                    result.getCouponList() != null ? result.getCouponList().size() : 0,
                    result.getResultCode());

            return result;

        } catch (Exception e) {
            log.error("❌ [CrmCouponTool] Error fetching coupons: {}", e.getMessage(), e);
            return errorResponse();
        }
    }

    /**
     * Maps brand concept to CRM ouCode.
     */
    static String resolveOuCode(String concept) {
        if (concept == null) return "LS";
        return switch (concept.toUpperCase()) {
            case "MAX"        -> "MAX";
            case "HOMECENTRE" -> "HC";
            case "BABYSHOP"   -> "BS";
            default           -> "LS";   // LIFESTYLE is the default
        };
    }

    private CrmCouponResponse errorResponse() {
        CrmCouponResponse r = new CrmCouponResponse();
        r.setChatMessage("Unable to fetch your coupons right now. Please try again later or visit the app.");
        return r;
    }
}
