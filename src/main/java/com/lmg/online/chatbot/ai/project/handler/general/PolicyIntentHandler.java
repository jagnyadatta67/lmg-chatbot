package com.lmg.online.chatbot.ai.project.handler.general;

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.analytics.TokenCostCalculator;
import com.lmg.online.chatbot.ai.analytics.TokenUsage;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;


import com.lmg.online.chatbot.ai.project.doc.vector.config.chroma.VectorStoreFactoryRedis;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PolicyIntentHandler implements IntentHandler<String> {

    private static final Pattern POLICY_QUESTION_PATTERN = Pattern.compile(
            ".*\\b(" +
                    "policy|return|refund|exchange|cancel|cancellation|replace|replacement|" +
                    "shipping|delivery\\s*charges|delivery\\s*policy|return\\s*policy|" +
                    "exchange\\s*policy|refund\\s*policy|cancel\\s*policy|" +
                    "how\\s*to\\s*(return|cancel|exchange)|" +
                    "when\\s*will\\s*I\\s*get\\s*refund|" +
                    "charges\\s*for\\s*delivery|free\\s*shipping|" +
                    "return\\s*window|refund\\s*time|order\\s*cancel|" +
                    "modify\\s*order|replace\\s*item" +
                    ")\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final int SIMILARITY_TOP_K = 5;

    @Autowired
    @Qualifier("policyClient")
    private ChatClient policyClient;

    private final VectorStoreFactoryRedis vectorStoreFactory;
    private final TokenCostCalculator tokenCostCalculator;
    private final AiAnalyticsService aiAnalyticsService;

    public PolicyIntentHandler(VectorStoreFactoryRedis vectorStoreFactory,
                               TokenCostCalculator tokenCostCalculator,
                               AiAnalyticsService aiAnalyticsService) {
        this.vectorStoreFactory  = vectorStoreFactory;
        this.tokenCostCalculator = tokenCostCalculator;
        this.aiAnalyticsService  = aiAnalyticsService;
    }

    // ── IntentHandler contract ────────────────────────────────────────────────

    @Override
    public ChatbotResponse<String> handle(ChatRequest req, long startTime) {
        log.info("📋 POLICY_QUESTION for concept={}", req.getConcept());

        // 1. Fetch RAG docs first — if none found, escalate to Write-to-Us
        VectorStore vectorStore = vectorStoreFactory.getVectorStore(req.getConcept());
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(req.getMessage())
                        .topK(SIMILARITY_TOP_K)
                        .build()
        );
        log.info("📚 Found {} relevant policy chunks for concept={}", docs.size(), req.getConcept());

        if (docs.isEmpty()) {
            log.info("📭 No RAG context — escalating to WRITE_US for concept={}", req.getConcept());
            return ChatbotResponse.<String>builder()
                    .data("I'm sorry, I couldn't find specific information on that. Would you like to raise a support ticket and our team will get back to you?")
                    .intent("WRITE_US")
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        // 2. RAG docs found — call AI with context
        ChatResponse response = callWithDocs(req, docs);
        return buildResponse(response, req, startTime);
    }

    @Override
    public String getIntentType() {
        return "POLICY_QUESTION";
    }

    @Override
    public boolean canHandle(String query) {
        return POLICY_QUESTION_PATTERN.matcher(query.toLowerCase()).matches();
    }

    // ── RAG AI call (only invoked when docs are non-empty) ───────────────────

    private ChatResponse callWithDocs(ChatRequest req, List<Document> docs) {
        String systemPrompt = buildSystemPrompt(req.getConcept());
        String userPrompt   = buildUserPrompt(req.getMessage(), docs, req.getPreviousResponse());

        return policyClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();
    }

    /**
     * System prompt — defines the assistant persona and strict response rules.
     * Sent once as the "system" role; not repeated in the user turn.
     */
    private String buildSystemPrompt(String concept) {
        String brand = concept != null ? concept.trim() : "Landmark Group";
        String phone = ConceptBaseUrlResolver.getRawPhoneNumber(brand);

        return String.format("""
                You are a friendly Policy & FAQ assistant for %s (Landmark Group India).
                Help customers understand policies and procedures clearly.

                === FORMATTING RULES (MANDATORY) ===
                - NEVER write long paragraphs or big statements
                - ALWAYS use short bullet points (• ) or numbered steps (1. 2. 3.)
                - Each point must be ONE short sentence — max 12 words per point
                - Start with a one-line direct answer, then list the points
                - Always end with: "📞 For help, call us at %s."

                === RESPONSE RULES ===

                RULE 1 — PROCESS QUESTIONS (how to return / cancel / exchange / track):
                Use numbered steps. Example format:
                  Here's how you can [action]:
                  1. [Step one]
                  2. [Step two]
                  3. [Step three]
                  📞 For help, call us at %s.

                RULE 2 — POLICY QUESTIONS (timelines / charges / eligibility / conditions):
                Use bullet points. Example format:
                  Here's what you need to know:
                  • [Point one]
                  • [Point two]
                  • [Point three]
                  📞 For help, call us at %s.

                RULE 3 — NO CONTEXT / NOT FOUND:
                Reply:
                  I'm sorry, I don't have that information right now.
                  📞 For accurate details, call us at %s.

                RULE 4 — COMPLAINTS / DAMAGED ITEMS:
                Reply empathetically:
                  I'm sorry to hear about this! 😔
                  📞 Please call our support team at %s for urgent help.

                === STRICT PROHIBITIONS ===
                - Never write paragraphs — only bullets or numbered lists
                - Never make up any policy detail, amount, or date
                - Never say "based on my training data" or "as an AI"
                """,
                brand, phone, phone, phone, phone, phone);
    }

    /**
     * User prompt — contains the retrieved RAG context and the customer's question.
     * Lean and structured so the LLM knows exactly what is grounded vs. unknown.
     */
    private String buildUserPrompt(String question, List<Document> docs, String previousResponse) {
        String context = docs.isEmpty()
                ? "[No matching policy documents found in knowledge base]"
                : docs.stream()
                      .map(d -> "• " + d.getText().trim())
                      .collect(Collectors.joining("\n\n"));

        StringBuilder sb = new StringBuilder();

        if (previousResponse != null && !previousResponse.isBlank()) {
            sb.append("=== PREVIOUS ASSISTANT REPLY ===\n")
              .append(previousResponse.trim())
              .append("\n\n");
        }

        sb.append("=== RETRIEVED POLICY CONTEXT ===\n")
          .append(context)
          .append("\n\n")
          .append("=== CUSTOMER QUESTION ===\n")
          .append(question.trim());

        return sb.toString();
    }



    private ChatbotResponse<String> buildResponse(
            ChatResponse response, ChatRequest request, long startTime) {
        long responseTime = System.currentTimeMillis() - startTime;
        String data = response.getResult().getOutput().getText();
        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                response.getMetadata().getUsage(),
                response.getMetadata().getModel()
        );
        trackAnalytics(request, response, responseTime);
        return ChatbotResponse.<String>builder()
                .data(data)
                .tokenUsage(tokens)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }

    private void trackAnalytics(ChatRequest request, ChatResponse response, long responseTime) {
        var usage = response.getMetadata().getUsage();

        aiAnalyticsService.trackUsage(
                getIntentType(),
                getIntentType(),
                request.getMessage(),
                response.getResult().getOutput().getText(),
                usage != null ? usage.getPromptTokens().intValue()     : 0,
                usage != null ? usage.getCompletionTokens().intValue() : 0,
                response.getMetadata().getModel(),
                response.getResult().getMetadata().getFinishReason(),
                true,
                "policySearch",
                responseTime
        );

        if (usage != null) {
            log.info("📊 {} - Tokens: {} (↑{} ↓{}), Time: {}ms",
                    getIntentType(), usage.getTotalTokens(), usage.getPromptTokens(),
                    usage.getCompletionTokens(), responseTime);
        } else {
            log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);
        }
    }
}