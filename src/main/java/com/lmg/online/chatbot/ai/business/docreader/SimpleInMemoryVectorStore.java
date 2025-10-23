package com.lmg.online.chatbot.ai.business.docreader;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SimpleInMemoryVectorStore {

    private final EmbeddingModel embeddingModel;
    private final List<DocumentWithEmbedding> documents = new ArrayList<>();

    public SimpleInMemoryVectorStore(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public void add(List<Document> docs) {
        for (Document doc : docs) {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(doc.getFormattedContent()));
            float[] embedding = response.getResults().get(0).getOutput();
            documents.add(new DocumentWithEmbedding(doc, embedding));
        }
    }

    public List<Document> similaritySearch(String query, int topK) {
        EmbeddingResponse queryResponse = embeddingModel.embedForResponse(List.of(query));
        float[] queryEmbedding = queryResponse.getResults().get(0).getOutput();

        return documents.stream()
                .map(doc -> new ScoredDocument(doc.document,
                        cosineSimilarity(queryEmbedding, doc.embedding)))
                .sorted(Comparator.comparingDouble(sd -> -sd.score))
                .limit(topK)
                .map(sd -> sd.document)
                .collect(Collectors.toList());
    }

    private double cosineSimilarity(float[] vec1, float[] vec2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    record DocumentWithEmbedding(Document document, float[] embedding) {}
    record ScoredDocument(Document document, double score) {}
}