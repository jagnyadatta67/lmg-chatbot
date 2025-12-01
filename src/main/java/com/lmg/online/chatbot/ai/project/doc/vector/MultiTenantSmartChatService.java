package com.lmg.online.chatbot.ai.project.doc.vector;

import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.project.doc.vector.config.VectorStoreFactory;
import com.lmg.online.chatbot.ai.project.doc.vector.config.chroma.VectorStoreFactoryRedis;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Multi-Tenant Smart Chat Service
 * Key Features:
 * - Concept-specific policy retrieval (each website has own policies)
 * - Shared order tracking (common across all concepts)
 * - Dynamic model selection
 * - Optimized token usage
 */
@Service
@Slf4j
public class MultiTenantSmartChatService {

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStoreFactoryRedis vectorStoreFactory;

    public MultiTenantSmartChatService(ChatClient.Builder chatClientBuilder,  VectorStoreFactoryRedis vectorStoreFactory) {
        this.chatClientBuilder = chatClientBuilder;

        this.vectorStoreFactory = vectorStoreFactory;

    }
    /**
     * Policy Question - Uses CONCEPT-SPECIFIC vector store
     * THIS IS THE KEY: Different policies for each website!
     */
    public ChatResponse handlePolicyQuestion(ChatRequest req) {
        log.info("📋 POLICY QUESTION for concept: {} | Query: {}", req.getConcept(), req.getMessage());
        VectorStore conceptVectorStore = vectorStoreFactory.getVectorStore(req.getConcept());


        List<Document> docs = conceptVectorStore.similaritySearch(req.getMessage()); // Reduced from 5
        String context = docs.stream()
                .map(Document::getFormattedContent)
                .limit(3)
                .collect(Collectors.joining("\n"));

             String phone = ConceptBaseUrlResolver.getPhoneNumber(req.getConcept());

        String safePhone = (phone != null && !phone.isBlank()) ? phone : "our customer care team";

        String prompt = String.format(
                "Context:\n%s\n\n" +
                        "Q: %s\n\n" +
                        "A: Answer strictly using the above context.\n" +
                        "✅ Use short, numbered points (1., 2., 3., etc.) — no paragraphs.\n" +
                        "✅ Avoid long explanations, filler words, or unrelated info.\n" +
                        "✅ Be concise, clear, and polite.\n" +
                        "✅ Always end with this exact line:\n" +
                        "👉 For more help, contact our customer care: %s",
                context,
                req.getMessage(),
                safePhone
        );







        // Build context from concept-specific documents

        log.info("📚 Found {} relevant policy docs for {}", docs.size(), req.getConcept());

        // Select model
        String model = "gpt-4o-mini-2024-07-18";
        ChatClient client = createClient(model, 800);



       return  client.prompt().user(prompt).call().chatResponse();


    }



    // Helper methods

    private ChatClient createClient(String model, int maxTokens) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.0)
                .maxTokens(maxTokens)
                .build();

        return chatClientBuilder.defaultOptions(options).build();
    }


}