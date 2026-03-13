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
        ChatResponse response = handlePolicyQuestion(req);
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

    // ── RAG core (inlined from deleted MultiTenantSmartChatService) ──────────

    private ChatResponse handlePolicyQuestion(ChatRequest req) {
        String concept  = req.getConcept();
        String question = req.getMessage();

        // 1. Get concept-specific Redis vector store
        VectorStore vectorStore = vectorStoreFactory.getVectorStore(concept);

        // 2. Retrieve most-relevant policy chunks
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(SIMILARITY_TOP_K)
                        .build()
        );
        log.info("📚 Found {} relevant policy chunks for concept={}", docs.size(), concept);

        // 3. Build grounded prompt and call the policy LLM
        String prompt = buildRagPrompt(concept, question, docs);
        return policyClient.prompt()
                .user(prompt)
                .call()
                .chatResponse();
    }

    private String buildRagPrompt(String concept, String question, List<Document> docs) {
        String context = docs.isEmpty()
                ? "No specific policy documents found."
                : docs.stream()
                      .map(Document::getText)
                      .collect(Collectors.joining("\n\n---\n\n"));

        String contactLine = ConceptBaseUrlResolver.getPhoneNumber(concept);

        return String.format("""
                You are a helpful customer support agent for %s.
                Use ONLY the policy information provided below to answer the question.
                If the answer is not in the provided context, say you don't have that information
                and provide the support contact: %s

                === POLICY CONTEXT ===
                %s
                =====================

                Customer question: %s

                Answer helpfully and concisely based on the policy context above.
                """,
                concept, contactLine, context, question);
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
                usage.getPromptTokens().intValue(),
                usage.getCompletionTokens().intValue(),
                response.getMetadata().getModel(),
                response.getResult().getMetadata().getFinishReason(),
                true,
                "policySearch",
                responseTime
        );

        log.info("📊 {} - Tokens: {} (↑{} ↓{}), Time: {}ms",
                getIntentType(), usage.getTotalTokens(), usage.getPromptTokens(),
                usage.getCompletionTokens(), responseTime);
    }
}