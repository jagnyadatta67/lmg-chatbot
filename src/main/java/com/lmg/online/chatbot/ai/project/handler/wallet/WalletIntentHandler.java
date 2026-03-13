package com.lmg.online.chatbot.ai.project.handler.wallet;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.wallet.WalletTool;
import com.lmg.online.chatbot.ai.tools.wallet.dto.WalletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Handles WALLET_BALANCE intent — returns customer's store credit / wallet balance.
 * Calls WalletTool directly (no AI needed — no parameters to extract).
 *
 * Triggered by: "my wallet balance", "store credit", "how much credit do I have",
 *               "cashback balance", "available wallet amount"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletIntentHandler implements IntentHandler<WalletResponse> {

    private static final Pattern WALLET_PATTERN = Pattern.compile(
            ".*\\b(wallet|store\\s*credit|cashback|credit\\s*balance|" +
            "my\\s*credit|available\\s*credit|wallet\\s*balance|wallet\\s*amount)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private final WalletTool walletTool;
    private final AiAnalyticsService aiAnalyticsService;

    @Override
    public ChatbotResponse<WalletResponse> handle(ChatRequest request, long startTime) {
        log.info("💰 Handling WALLET_BALANCE intent");

        if (!isUserAuthenticated(request)) {
            return unauthenticatedResponse(startTime);
        }

        WalletResponse data = walletTool.getWalletBalance(
                request.getUserId(),
                request.getAccessToken(),
                request.getConcept(),
                request.getEnv(),
                request.getAppid()
        );

        return buildResponse(data, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "WALLET_BALANCE";
    }

    @Override
    public boolean canHandle(String query) {
        return WALLET_PATTERN.matcher(query).matches();
    }

    private boolean isUserAuthenticated(ChatRequest request) {
        return request.getUserId() != null && !request.getUserId().isBlank();
    }

    private ChatbotResponse<WalletResponse> unauthenticatedResponse(long startTime) {
        WalletResponse r = new WalletResponse();
        r.setChatMessage("Please sign in — once logged in, I can show your wallet balance.");
        return buildResponse(r, null, startTime);
    }

    private ChatbotResponse<WalletResponse> buildResponse(
            WalletResponse data, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;
        String rawMessage = request != null ? request.getMessage() : "";

        aiAnalyticsService.trackUsage(
                null, null,
                rawMessage,
                data.getChatMessage() != null ? data.getChatMessage() : "",
                0, 0,
                "gpt-4o-mini",
                "stop",
                true,
                "getWalletBalance",
                responseTime
        );

        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);

        return ChatbotResponse.<WalletResponse>builder()
                .data(data)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }
}
