package com.lmg.online.chatbot.ai.project.handler.order;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.analytics.TokenCostCalculator;
import com.lmg.online.chatbot.ai.analytics.TokenUsage;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.project.doc.vector.config.chroma.VectorStoreFactoryRedis;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.order.OrderTrackingTool;
import com.lmg.online.chatbot.ai.tools.order.dto.CancelReturnResponse;
import com.lmg.online.chatbot.ai.tools.order.dto.ChatbotOrderTrackingResponse;
import com.lmg.online.chatbot.ai.tools.order.dto.HybrisSingleOrderResponse;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Handles CANCEL_OR_RETURN intent — customer wants to cancel, return, or exchange an order.
 *
 * <p>The chatbot CANNOT perform cancellations or returns itself.
 * This handler explains the policy and shows the specific order (if a number is provided).
 *
 * <p>Flow:
 * <ol>
 *   <li>Extract order number from message (optional)</li>
 *   <li>If order number present AND user is authenticated → try fetching the order:
 *       <ul>
 *         <li>Found   → populate {@code orderData} in response</li>
 *         <li>Not found → set {@code needsOrderNumber=true}, continue to policy</li>
 *       </ul>
 *   </li>
 *   <li>Always fetch cancel/return policy from RAG</li>
 *   <li>Return {@link CancelReturnResponse} with order data (optional) + policy text</li>
 * </ol>
 */
@Slf4j
@Component
public class CancelOrReturnIntentHandler implements IntentHandler<CancelReturnResponse> {

    /**
     * The 4 actions this handler covers — cancel, return, exchange, refund.
     * ALL must be accompanied by an ORDER context (personal possessive, order-related
     * noun, or an explicit order number).  Generic policy questions ("what is the return
     * policy", "cancellation charges") intentionally do NOT match — they route to
     * POLICY_QUESTION instead.
     *
     * Coverage:
     *   1. action + possessive/demonstrative + order-noun  → "cancel my order", "return this item"
     *   2. possessive/demonstrative + order-noun + action  → "my order cancel", "this product return"
     *   3. strong action-intent verb + action keyword      → "I want to cancel", "can I return", "how can I exchange"
     *      (these always imply the customer's own order, never a policy question)
     *   4. order number (7–12 digits) + action            → "9419472006 cancel", "cancel 9419472006"
     */
    private static final Pattern CANCEL_RETURN_PATTERN = Pattern.compile(
            ".*(" +

            // Rule 1 — action + personal/demonstrative + order noun
            "\\b(cancel|return|exchange|refund)\\b.*\\b(my|this|the)\\b.*\\b(order|item|product|purchase|it)\\b|" +

            // Rule 2 — personal/demonstrative + order noun + action
            "\\b(my|this|the)\\b.*\\b(order|item|product|purchase)\\b.*\\b(cancel|return|exchange|refund)\\b|" +

            // Rule 3 — explicit personal action-intent: "I want/need/can I/how can I + action"
            // These always mean the customer's own order, not a policy question.
            "\\b(i\\s+want\\s+to|i\\s+need\\s+to|i\\s+would\\s+like\\s+to|can\\s+i|how\\s+can\\s+i|how\\s+do\\s+i)\\s+" +
                "(cancel|return|exchange|get\\s+a?\\s*refund)\\b|" +

            // Rule 4 — order number (7–12 digits) paired with action (any order)
            "\\d{7,12}.*\\b(cancel|return|exchange|refund)\\b|" +
            "\\b(cancel|return|exchange|refund)\\b.*\\d{7,12}" +

            ").*",
            Pattern.CASE_INSENSITIVE
    );

    /** Extracts 7–12 digit numeric order numbers from free text. */
    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("\\b(\\d{7,12})\\b");

    private static final int SIMILARITY_TOP_K = 5;

    // ── Dependencies ─────────────────────────────────────────────────────────

    @Autowired
    @Qualifier("cancelReturnClient")
    private ChatClient cancelReturnClient;

    private final VectorStoreFactoryRedis vectorStoreFactory;
    private final OrderTrackingTool       orderTrackingTool;
    private final TokenCostCalculator     tokenCostCalculator;
    private final AiAnalyticsService      aiAnalyticsService;

    public CancelOrReturnIntentHandler(VectorStoreFactoryRedis vectorStoreFactory,
                                       OrderTrackingTool orderTrackingTool,
                                       TokenCostCalculator tokenCostCalculator,
                                       AiAnalyticsService aiAnalyticsService) {
        this.vectorStoreFactory  = vectorStoreFactory;
        this.orderTrackingTool   = orderTrackingTool;
        this.tokenCostCalculator = tokenCostCalculator;
        this.aiAnalyticsService  = aiAnalyticsService;
    }

    // ── IntentHandler contract ────────────────────────────────────────────────

    @Override
    public ChatbotResponse<CancelReturnResponse> handle(ChatRequest request, long startTime) {
        log.info("🔄 Handling CANCEL_OR_RETURN, userId={}, concept={}",
                request.getUserId(), request.getConcept());

        CancelReturnResponse response = new CancelReturnResponse();

        // ── Step 1: Try to fetch the order if order number present ──────────
        String orderNo = resolveOrderNumber(request);
        if (orderNo != null) {
            if (isAuthenticated(request)) {
                log.info("🔢 Fetching order {} for cancel/return intent", orderNo);
                tryFetchOrder(request, orderNo, response);
            } else {
                // Has order number but not logged in
                response.setChatMessage(
                        "Please sign in to view order details — once logged in I can show your order.");
            }
        }

        // ── Step 2: Always fetch cancel/return policy from RAG ───────────────
        String policyText = fetchPolicy(request);
        response.setPolicyText(policyText);

        return buildResponse(response, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "CANCEL_OR_RETURN";
    }

    @Override
    public boolean canHandle(String query) {
        return CANCEL_RETURN_PATTERN.matcher(query).matches();
    }

    // ── Order fetch ───────────────────────────────────────────────────────────

    private void tryFetchOrder(ChatRequest request, String orderNo, CancelReturnResponse response) {
        try {
            HybrisSingleOrderResponse hybrisResponse = orderTrackingTool.getSingleOrderDetails(
                    request.getUserId(),
                    request.getAccessToken(),
                    orderNo,
                    request.getConcept(),
                    request.getEnv(),
                    request.getAppid()
            );

            if (hybrisResponse == null || hybrisResponse.getCode() == null || hybrisResponse.getCode().isBlank()) {
                log.warn("⚠️ Order {} not found for userId={}", orderNo, request.getUserId());
                response.setNeedsOrderNumber(true);
                response.setChatMessage(
                        "I couldn't find order <b>" + orderNo + "</b>. " +
                        "Please check the order number and try again.");
                return;
            }

            ChatbotOrderTrackingResponse.OrderSummary orderSummary =
                    mapToOrderSummary(hybrisResponse, request.getConcept(), request.getEnv());

            if (orderSummary.getEntries() == null || orderSummary.getEntries().isEmpty()) {
                log.warn("⚠️ Order {} returned empty entries for userId={}", orderNo, request.getUserId());
                response.setNeedsOrderNumber(true);
                response.setChatMessage(
                        "I couldn't find order <b>" + orderNo + "</b>. " +
                        "Please check the order number and try again.");
                return;
            }

            log.info("✅ Order {} fetched — {} item(s)", orderNo, orderSummary.getEntries().size());
            response.setOrderData(orderSummary);

        } catch (Exception e) {
            log.error("❌ Error fetching order {} : {}", orderNo, e.getMessage(), e);
            response.setNeedsOrderNumber(true);
            response.setChatMessage(
                    "I couldn't retrieve order <b>" + orderNo + "</b> right now. " +
                    "Please try again or contact support. " +
                    ConceptBaseUrlResolver.getPhoneNumber(request.getConcept()));
        }
    }

    /**
     * Maps a raw {@link HybrisSingleOrderResponse} into the widget-compatible
     * {@link ChatbotOrderTrackingResponse.OrderSummary} used by the cancel/return card.
     */
    private ChatbotOrderTrackingResponse.OrderSummary mapToOrderSummary(
            HybrisSingleOrderResponse hybris, String concept, String env) {

        ChatbotOrderTrackingResponse.OrderSummary summary = new ChatbotOrderTrackingResponse.OrderSummary();
        summary.setOrderNo(hybris.getCode());
        summary.setOrderStatus(
                hybris.getStatusDisplay() != null ? hybris.getStatusDisplay() : hybris.getStatus());
        summary.setOrderDate(hybris.getCreated());
        summary.setOrderAmount(hybris.getTotalPrice() != null ? hybris.getTotalPrice().getValue() : null);

        List<HybrisSingleOrderResponse.HybrisEntry> hybrisEntries =
                hybris.getEntries() != null ? hybris.getEntries() : Collections.emptyList();

        List<ChatbotOrderTrackingResponse.OrderEntry> entries = hybrisEntries.stream()
                .map(he -> {
                    ChatbotOrderTrackingResponse.OrderEntry entry =
                            new ChatbotOrderTrackingResponse.OrderEntry();
                    if (he.getEntryNumber() != null) entry.setPosition(he.getEntryNumber());
                    entry.setOrderEntryPk(he.getOrderEntryPk());
                    entry.setQuantity(he.getQuantity() != null ? he.getQuantity() : 1);
                    entry.setReturnable(he.isReturnEligible());
                    entry.setExchangeable(he.isExchangeEnabled());
                    if (he.getProduct() != null) {
                        entry.setProductCode(he.getProduct().getCode());
                        entry.setProductUrl(he.getProduct().getUrl());
                        if (he.getProduct().getImages() != null && !he.getProduct().getImages().isEmpty()) {
                            String imgUrl = he.getProduct().getImages().get(0).getUrl();
                            if (imgUrl != null && !imgUrl.isBlank()) {
                                entry.setProductImage(imgUrl.startsWith("http") ? imgUrl
                                        : ConceptBaseUrlResolver.getEnvBaseUrl(concept, env) + imgUrl);
                            }
                        }
                    }
                    return entry;
                })
                .collect(Collectors.toList());

        summary.setEntries(entries);
        return summary;
    }

    // ── RAG policy fetch ──────────────────────────────────────────────────────

    private String fetchPolicy(ChatRequest request) {
        try {
            VectorStore vectorStore = vectorStoreFactory.getVectorStore(request.getConcept());

            // Broad query covering all 4 action types — ensures relevant policy chunks are fetched
            String ragQuery = "cancel cancellation return exchange refund policy timeline eligibility " + request.getMessage();
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(ragQuery).topK(SIMILARITY_TOP_K).build()
            );

            log.info("📚 Found {} cancel/return policy chunks for concept={}", docs.size(), request.getConcept());

            if (docs.isEmpty()) {
                return "For cancellation and return information, please contact our support team. " +
                       ConceptBaseUrlResolver.getPhoneNumber(request.getConcept());
            }

            ChatResponse aiResponse = cancelReturnClient.prompt()
                    .system(buildSystemPrompt(request.getConcept()))
                    .user(buildUserPrompt(request.getMessage(), docs))
                    .call()
                    .chatResponse();

            trackAnalytics(request, aiResponse, 0);
            return aiResponse.getResult().getOutput().getText();

        } catch (Exception e) {
            log.error("❌ Error fetching cancel/return policy for concept={}: {}", request.getConcept(), e.getMessage(), e);
            return "For cancellation and return details, please contact our support team. " +
                   ConceptBaseUrlResolver.getPhoneNumber(request.getConcept());
        }
    }

    // ── Prompts ───────────────────────────────────────────────────────────────

    private String buildSystemPrompt(String concept) {
        String brand = concept != null ? concept.trim() : "Landmark Group";
        String phone = ConceptBaseUrlResolver.getRawPhoneNumber(brand);

        return String.format("""
                You are a customer support assistant for %s (Landmark Group India).
                The customer is asking about one of these 4 order actions:
                  CANCEL · RETURN · EXCHANGE · REFUND

                ⚠️ CRITICAL: This chatbot CANNOT perform any of these actions.
                You can only explain the policy and guide the customer on how to do it themselves.

                === DETECT THE ACTION TYPE from the customer's message ===
                Match one of: CANCEL / RETURN / EXCHANGE / REFUND — then apply the matching rule below.
                If multiple actions are mentioned, address all of them.

                === FORMATTING RULES (MANDATORY) ===
                - Start with one empathetic line: "I understand you'd like to [cancel/return/exchange/get a refund on] your order."
                - Use bullet points (• ) for policy conditions
                - Use numbered steps (1. 2. 3.) for HOW TO process
                - Each bullet/step: ONE short sentence, max 12 words
                - Always end with: "📞 For immediate help, call us at %s."
                - NEVER write paragraphs

                === RULE 1 — CANCELLATION ===
                  I understand you'd like to cancel your order.
                  • [Eligibility condition from context — e.g. before dispatch only]
                  • [Any cancellation window or restriction from context]
                  How to cancel:
                  1. Go to My Account → My Orders
                  2. Select the order → tap Cancel Order
                  3. Choose a reason and confirm
                  📞 For immediate help, call us at %s.

                === RULE 2 — RETURN ===
                  I understand you'd like to return your item.
                  • [Return window from context — e.g. within 30 days]
                  • [Condition eligibility — tags intact, unused, etc.]
                  • [Items not eligible for return — if in context]
                  How to initiate a return:
                  1. Go to My Account → My Orders
                  2. Select the item → tap Return
                  3. Choose reason → schedule pickup
                  📞 For immediate help, call us at %s.

                === RULE 3 — EXCHANGE ===
                  I understand you'd like to exchange your item.
                  • [Exchange window and conditions from context]
                  • [Size/colour exchange availability from context]
                  How to exchange:
                  1. Go to My Account → My Orders
                  2. Select the item → tap Exchange
                  3. Choose new size/colour → confirm
                  📞 For immediate help, call us at %s.

                === RULE 4 — REFUND ===
                  I understand you're asking about your refund.
                  • [Refund mode from context — original payment method / wallet / etc.]
                  • [Refund timeline from context — e.g. 7–10 business days]
                  • Refunds are processed only after the return is picked up and verified.
                  📞 For immediate help, call us at %s.

                === STRICT PROHIBITIONS ===
                - NEVER say "I can cancel / return / exchange your order for you"
                - NEVER make up policy details, timelines, amounts, or eligibility rules
                - NEVER write paragraphs — only bullets and numbered lists
                - Only use facts from the retrieved policy context provided below
                - If a detail is not in context, skip it — do not invent it
                """,
                brand, phone, phone, phone, phone, phone, phone);
    }

    private String buildUserPrompt(String question, List<Document> docs) {
        String context = docs.stream()
                .map(d -> "• " + d.getText().trim())
                .collect(Collectors.joining("\n\n"));

        return "=== RETRIEVED POLICY CONTEXT ===\n" +
               context +
               "\n\n=== CUSTOMER QUESTION ===\n" +
               question.trim();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveOrderNumber(ChatRequest request) {
        if (StringUtils.isNotEmpty(request.getOrderNo())) {
            String candidate = request.getOrderNo().trim();
            if (candidate.matches("\\d{7,12}")) return candidate;
        }
        if (StringUtils.isNotEmpty(request.getMessage())) {
            Matcher m = ORDER_NUMBER_PATTERN.matcher(request.getMessage());
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private boolean isAuthenticated(ChatRequest request) {
        return StringUtils.isNotEmpty(request.getUserId())
                && StringUtils.isNotEmpty(request.getAccessToken());
    }

    // ── Response builder ──────────────────────────────────────────────────────

    private ChatbotResponse<CancelReturnResponse> buildResponse(
            CancelReturnResponse data, ChatRequest request, long startTime) {

        long responseTime = System.currentTimeMillis() - startTime;
        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);

        aiAnalyticsService.trackUsage(
                null, null,
                request != null ? request.getMessage() : "",
                data.getPolicyText(),
                0, 0,
                "gpt-4o-mini",
                "stop",
                true,
                "cancelReturnPolicy",
                responseTime
        );

        return ChatbotResponse.<CancelReturnResponse>builder()
                .data(data)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }

    private void trackAnalytics(ChatRequest request, ChatResponse response, long responseTime) {
        var usage = response.getMetadata().getUsage();
        if (usage != null) {
            log.info("📊 {} RAG - Tokens: {} (↑{} ↓{})",
                    getIntentType(), usage.getTotalTokens(),
                    usage.getPromptTokens(), usage.getCompletionTokens());
        }
    }
}
