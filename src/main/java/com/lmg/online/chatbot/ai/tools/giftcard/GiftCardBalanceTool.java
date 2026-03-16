package com.lmg.online.chatbot.ai.tools.giftcard;

import com.lmg.online.chatbot.ai.auth.AnonymousTokenService;
import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.giftcard.dto.GiftCardBalanceRequest;
import com.lmg.online.chatbot.ai.tools.giftcard.dto.GiftCardBalanceResponse;
import com.lmg.online.chatbot.ai.tools.giftcard.dto.GiftCardError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Calls the Hybris gift-card balance endpoint directly (no AI involvement).
 *
 * <pre>
 *   POST /landmarkshopscommercews/v2/{siteId}/en/users/anonymous/gift-card/balance
 *        ?appId={appId}
 *
 *   Headers:
 *     Content-Type: application/json
 *     access_token: {accessToken}
 *
 *   Body: { "cardNumber": "...", "pin": "..." }
 * </pre>
 */
@Slf4j
@Component
public class GiftCardBalanceTool {

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    @Autowired
    private AnonymousTokenService anonymousTokenService;

    /**
     * Check the balance on a gift card.
     *
     * @param concept     Brand: LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
     * @param env         Environment: uat1 | uat5 | prod
     * @param accessToken Customer's OAuth access token (sent in header)
     * @param appId       App: ANDROID | IPHONE | Desktop | Mobile
     * @param cardNumber  Gift card number
     * @param pin         Gift card PIN (may be empty string)
     * @return Deserialized Hybris response, never null
     */
    public GiftCardBalanceResponse checkGiftCardBalance(
            String concept,
            String env,
            String accessToken,
            String appId,
            String cardNumber,
            String pin) {

        log.info("🎁 [GiftCardBalanceTool] concept={}, env={}, appId={}, card={}",
                concept, env, appId, maskCard(cardNumber));

        try {
            // Resolve token: use caller's token if present, otherwise fetch anonymous token
            String tokenToUse = (accessToken != null && !accessToken.isBlank())
                    ? accessToken
                    : anonymousTokenService.getToken(concept, env, appId);

            String url = ConceptBaseUrlResolver.buildApiUrl(
                    concept, env, "/en/users/anonymous/gift-card/balance", appId);

            log.info("🌐 [GiftCardBalanceTool] URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("access_token", tokenToUse);

            GiftCardBalanceRequest body = new GiftCardBalanceRequest(
                    cardNumber, pin != null ? pin : "");

            GiftCardBalanceResponse response = authenticationServiceUtil
                    .callWithAuthRetry(appId, url, HttpMethod.POST, headers, body,
                            GiftCardBalanceResponse.class, env)
                    .getBody();

            if (response == null) {
                return errorResponse(concept, "No response received from server.");
            }


            log.info("✅ [GiftCardBalanceTool] errorOccurred={}, active={}, amount={}",
                    response.isErrorOccurred(), response.isActive(),
                    response.getAmount() != null ? response.getAmount().getFormattedValue() : "null");
            if(!CollectionUtils.isEmpty(response.getErrors()) &&
                    ("lmg.giftcard.client.server.error".equalsIgnoreCase(response.getErrors().get(0).getReason())
                            || "lmg.giftcard.unsuccessful".equalsIgnoreCase(response.getErrors().get(0).getReason()))){


                log.info("✅ [GiftCardBalanceTool] Retry errorOccurred={}, active={}, amount={}",
                        response.isErrorOccurred(), response.isActive(),
                        response.getAmount() != null ? response.getAmount().getFormattedValue() : "null");
                 response = authenticationServiceUtil
                        .callWithAuthRetry(appId, url, HttpMethod.POST, headers, body,
                                GiftCardBalanceResponse.class, env)
                        .getBody();
                log.info("✅ [GiftCardBalanceTool] Retry Success errorOccurred={}, active={}, amount={}",
                        response.isErrorOccurred(), response.isActive(),
                        response.getAmount() != null ? response.getAmount().getFormattedValue() : "null");

            }

            return response;

        } catch (Exception e) {
            log.error("❌ [GiftCardBalanceTool] Failed: {}", e.getMessage(), e);
            return errorResponse(concept, null);
        }
    }

    private GiftCardBalanceResponse errorResponse(String concept, String detail) {
        GiftCardBalanceResponse r = new GiftCardBalanceResponse();
        GiftCardError err = new GiftCardError();
        err.setMessage("lmg.giftcard.client.server.error");
        err.setReason("SERVER_ERROR");
        r.setErrors(List.of(err));
        r.setChatMessage(detail != null ? detail :
                "Unable to check gift card balance right now. " +
                ConceptBaseUrlResolver.getPhoneNumber(concept));
        return r;
    }

    /** Mask card number for safe logging — show only last 4 digits */
    private String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        return "****" + cardNumber.substring(cardNumber.length() - 4);
    }
}
