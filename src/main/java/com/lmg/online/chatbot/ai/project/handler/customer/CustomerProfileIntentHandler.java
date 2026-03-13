package com.lmg.online.chatbot.ai.project.handler.customer;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.user.MyProfileDetailsTool;
import com.lmg.online.chatbot.ai.tools.user.dto.CustomerProfileResponseDTO;
import com.lmg.online.chatbot.ai.tools.user.dto.UserWsDTO;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Handles CUSTOMER_PROFILE intent.
 *
 * Flow (no AI):
 *   1. Auth guard — userId must be present
 *   2. Call MyProfileDetailsTool → Hybris v3 profile API → UserWsDTO
 *   3. Wrap in CustomerProfileResponseDTO
 *   4. Return ChatbotResponse
 *
 * The widget calls /api/user/profile directly for rendering,
 * but this response is still returned for non-widget API consumers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerProfileIntentHandler implements IntentHandler<CustomerProfileResponseDTO> {

    private static final Pattern PROFILE_PATTERN = Pattern.compile(
            ".*\\b(profile|my\\s*profile|account|my\\s*account|personal\\s*details|my\\s*details|" +
            "about\\s*me|user\\s*info|my\\s*info|update\\s*profile|edit\\s*profile)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private final MyProfileDetailsTool   myProfileDetailsTool;
    private final AiAnalyticsService     aiAnalyticsService;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ChatbotResponse<CustomerProfileResponseDTO> handle(ChatRequest request, long startTime) {
        log.info("👤 Handling CUSTOMER_PROFILE, userId={}", request.getUserId());

        // 1. Auth guard
        if (StringUtils.isEmpty(request.getUserId())) {
            return buildResponse(unauthResponse(), request, startTime);
        }

        // 2. Direct tool call — no AI
        UserWsDTO profile = myProfileDetailsTool.getProfile(
                request.getUserId(),
                request.getAccessToken(),
                request.getConcept(),
                request.getEnv(),
                request.getAppid()
        );

        // 3. Wrap in response DTO
        CustomerProfileResponseDTO data = new CustomerProfileResponseDTO();
        data.setCustomerProfile(profile);

        // If the tool set an error message, bubble it up
        if (StringUtils.isNotEmpty(profile.getChat_message())) {
            data.setChatMessage(profile.getChat_message());
        }

        log.info("✅ [CUSTOMER_PROFILE] uid={}, email={}",
                profile.getUid(), profile.getEmail());

        return buildResponse(data, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "CUSTOMER_PROFILE";
    }

    @Override
    public boolean canHandle(String query) {
        return PROFILE_PATTERN.matcher(query).matches();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CustomerProfileResponseDTO unauthResponse() {
        CustomerProfileResponseDTO r = new CustomerProfileResponseDTO();
        r.setChatMessage(
                "Please sign in to continue — once logged in I can fetch your profile details.");
        return r;
    }

    private ChatbotResponse<CustomerProfileResponseDTO> buildResponse(
            CustomerProfileResponseDTO data, ChatRequest request, long startTime) {

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
                "myProfileDetailsTool",
                responseTime
        );

        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);

        return ChatbotResponse.<CustomerProfileResponseDTO>builder()
                .data(data)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }
}
