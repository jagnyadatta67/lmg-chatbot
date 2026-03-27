package com.lmg.online.chatbot.ai.project.handler.crm;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.crm.CrmCouponTool;
import com.lmg.online.chatbot.ai.tools.crm.dto.CrmCouponResponse;
import com.lmg.online.chatbot.ai.tools.user.MyProfileDetailsTool;
import com.lmg.online.chatbot.ai.tools.user.dto.UserWsDTO;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Handles CRM_COUPON_LIST intent.
 *
 * Flow (no AI formatting):
 *   1. Auth guard — userId must be present
 *   2. Fetch user profile → extract signInMobile
 *   3. Map concept → ouCode
 *   4. Call CrmCouponTool → CRM API → CrmCouponResponse
 *   5. Return ChatbotResponse
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrmCouponListIntentHandler implements IntentHandler<CrmCouponResponse> {

    private static final Pattern COUPON_PATTERN = Pattern.compile(
            ".*\\b(coupon|coupons|offer|offers|discount|discounts|voucher|vouchers|promo|" +
            "promocode|promo\\s*code|deal|deals|my\\s*coupon|my\\s*offer|available\\s*offer|" +
            "reward|rewards|my\\s*reward|loyalty\\s*offer)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private final MyProfileDetailsTool myProfileDetailsTool;
    private final CrmCouponTool        crmCouponTool;
    private final AiAnalyticsService   aiAnalyticsService;

    @Override
    public ChatbotResponse<CrmCouponResponse> handle(ChatRequest request, long startTime) {
        log.info("🎟️ Handling CRM_COUPON_LIST, userId={}", request.getUserId());

        // 1. Auth guard
        if (StringUtils.isEmpty(request.getUserId())) {
            return buildResponse(unauthResponse(), request, startTime);
        }

        // 2. Fetch user profile to get mobile number
        UserWsDTO profile = myProfileDetailsTool.getProfile(
                request.getUserId(),
                request.getAccessToken(),
                request.getConcept(),
                request.getEnv(),
                request.getAppid()
        );

        if (StringUtils.isNotEmpty(profile.getChat_message())) {
            // Profile fetch failed — surface the error
            CrmCouponResponse err = new CrmCouponResponse();
            err.setChatMessage(profile.getChat_message());
            return buildResponse(err, request, startTime);
        }

        String mobileNumber = profile.getSignInMobile();

        if (StringUtils.isEmpty(mobileNumber)) {
            log.warn("⚠️ [CRM_COUPON_LIST] No mobile number found in profile for userId={}", request.getUserId());
            CrmCouponResponse err = new CrmCouponResponse();
            err.setChatMessage("We couldn't find a mobile number linked to your account. " +
                    "Please update your profile and try again.");
            return buildResponse(err, request, startTime);
        }

        // Strip country code prefix if present (e.g. "+91" or "91")
        String normalizedMobile = normalizeMobile(mobileNumber);

        // 3. Call CRM tool
        CrmCouponResponse data = crmCouponTool.getCoupons(normalizedMobile, request.getConcept());

        return buildResponse(data, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "CRM_COUPON_LIST";
    }

    @Override
    public boolean canHandle(String query) {
        return COUPON_PATTERN.matcher(query).matches();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Strips country code prefix so the CRM always receives a 10-digit number.
     * e.g. "+918105647414" → "8105647414", "918105647414" → "8105647414"
     */
    private String normalizeMobile(String mobile) {
        String digits = mobile.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            return digits.substring(2);
        }
        if (digits.length() == 13 && digits.startsWith("091")) {
            return digits.substring(3);
        }
        return digits;
    }

    private CrmCouponResponse unauthResponse() {
        CrmCouponResponse r = new CrmCouponResponse();
        r.setChatMessage("Please sign in to continue — once logged in I can fetch your available coupons.");
        return r;
    }

    private ChatbotResponse<CrmCouponResponse> buildResponse(
            CrmCouponResponse data, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;

        aiAnalyticsService.trackUsage(
                request != null ? request.getUserId() : null,
                null,
                request != null ? request.getMessage() : "",
                data.getChatMessage(),
                0, 0,
                "none",
                "stop",
                false,
                "crmCouponTool",
                responseTime
        );

        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);

        return ChatbotResponse.<CrmCouponResponse>builder()
                .data(data)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }
}
