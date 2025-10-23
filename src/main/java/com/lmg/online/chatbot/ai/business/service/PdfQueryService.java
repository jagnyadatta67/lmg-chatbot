package com.lmg.online.chatbot.ai.business.service;

import com.lmg.online.chatbot.ai.business.docreader.SimpleInMemoryVectorStore;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PdfQueryService {

    private final ChatClient chatClient;
    private final SimpleInMemoryVectorStore vectorStore;

    public PdfQueryService(ChatClient.Builder chatClientBuilder,
                           SimpleInMemoryVectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public String  askQuestion(String question) {
        // Search for relevant documents
        List<Document> relevantDocs = vectorStore.similaritySearch(question, 5);

        // Create context from retrieved documents
        String context = relevantDocs.stream()
                .map(Document::getFormattedContent)
                .collect(Collectors.joining("\n\n"));

        // Build prompt with context
        String prompt = """
You are the official LMG Online Assistant specializing in customer policies
such as shipping, returns, exchanges, and cancellations.

Answer the user's question based **only** on the context provided below.

--------------------
Context:
%s
--------------------

Question:
%s
--------------------

Instructions:
1️⃣ Use only the above context to answer — do not guess.
2️⃣ If the context does not clearly relate to the user's question,
    or if the answer is missing or incomplete,
    respond exactly as:
    "I don’t have enough information to answer that." %s
3️⃣ Keep your answer short, clear, and friendly.
4️⃣ Do not mention the word 'context' or any internal source names in your reply.

Now, generate your best possible answer: and  
""".formatted(context, question, ConceptBaseUrlResolver.conceptContactMap);


        // Get response from OpenAI
                return chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();
            }
        }