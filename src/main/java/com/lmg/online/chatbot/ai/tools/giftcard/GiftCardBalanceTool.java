package com.lmg.online.chatbot.ai.tools.giftcard;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.giftcard.dto.GiftCardBalanceRequest;
import com.lmg.online.chatbot.ai.tools.giftcard.dto.GiftCardBalanceResponse;
import com.lmg.online.chatbot.ai.tools.giftcard.dto.GiftCardError;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class GiftCardBalanceTool {

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    /**
     * Tool to check gift card balance
     */
    @Tool(
            name = "giftCardBalance",
            description = """
            Fetch the gift card balance details.

            Required Parameters:
            - concept: Concept name (e.g., LIFESTYLE, MAX, BABYSHOP)
            - env: Environment prefix (e.g., uat5, stg, prod)
            - accessToken: Access token (e.g., 'c8695a0d-1a5e-4ff5-b0b1-dbd2707f1ddc')
            - appId: Application identifier (e.g., 'Mobile', 'Desktop')
            - cardNumber: Gift card number to check
            - pin: Gift card pin (if applicable)
            
            Returns: GiftCardBalanceResponse return same dto no change
        """
    )
    public GiftCardBalanceResponse checkGiftCardBalance(
            String concept,
            String env,
            String accessToken,
            String appId,
            String cardNumber,
            String pin) {

        // Build endpoint URL
        String url = ConceptBaseUrlResolver.buildApiUrl(
                concept,
                env,
                "/en/users/anonymous/gift-card/balance",
                appId
        );

        // Prepare headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Prepare request body
        GiftCardBalanceRequest request = new GiftCardBalanceRequest(cardNumber, pin);

        try {
            // Execute call with retry handling
            GiftCardBalanceResponse giftCardBalanceResponse= authenticationServiceUtil
                    .callWithAuthRetry(appId, url, HttpMethod.POST, headers, request, GiftCardBalanceResponse.class, env)
                    .getBody();

            return  giftCardBalanceResponse;

        } catch (Exception e) {
            log.error("GC error ",e);
            GiftCardBalanceResponse giftCardBalanceResponse=new GiftCardBalanceResponse();
            GiftCardError gce=new GiftCardError();
            gce.setMessage("lmg.giftcard.client.server.error");
            gce.setReason("lmg.giftcard.client.server.error");
            giftCardBalanceResponse.setErrors(List.of(gce));
            return giftCardBalanceResponse;


        }
    }
}