package com.lmg.online.chatbot.ai.project.doc.vector.config;


import com.lmg.online.chatbot.ai.project.doc.vector.config.MultiTenantVectorService;
import io.qdrant.client.QdrantClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory to create and manage concept-specific vector stores dynamically
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreFactory {

    private final QdrantClient qdrantClient;
    private final EmbeddingModel embeddingModel;
    private final MultiTenantVectorService multiTenantVectorService;

    private final Map<String, VectorStore> vectorStoreCache = new ConcurrentHashMap<>();

    /**
     * Get or create concept-specific vector store
     *
     * @param concept Website concept (MAX, LIFESTYLE, BABYSHOP, HOMECENTRE)
     * @return Vector store for that concept
     */
    public VectorStore getVectorStore(String concept) {
        String normalizedConcept = concept.toUpperCase();
        return vectorStoreCache.computeIfAbsent(normalizedConcept, c -> {
            String collectionName = "chatbot_policy_" + c.toLowerCase();
            log.info("🔧 Creating vector store for concept: {} (collection: {})", c, collectionName);

            // Ensure collection exists before creating vector store
            multiTenantVectorService.ensureCollectionExists(collectionName);

            return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                    .collectionName(collectionName)
                    .initializeSchema(true)
                    .build();
        });
    }

    /**
     * Get all cached vector stores
     */
    public Map<String, VectorStore> getAllVectorStores() {
        return Map.copyOf(vectorStoreCache);
    }

    /**
     * Preload known concept stores
     */
    public void initializeAllConcepts() {
        String[] concepts = {"MAX", "LIFESTYLE", "BABYSHOP", "HOMECENTRE"};
        log.info("🚀 Initializing concept vector stores...");
        for (String concept : concepts) {
            getVectorStore(concept);
        }
        log.info("✅ Pre-initialized {} concept vector stores", concepts.length);
    }

    /**
     * Clear a specific concept from cache
     */
    public void clearConcept(String concept) {
        String normalizedConcept = concept.toUpperCase();
        vectorStoreCache.remove(normalizedConcept);
        log.info("🗑️ Cleared vector store cache for concept: {}", normalizedConcept);
    }

    /**
     * Clear all cached vector stores
     */
    public void clearAll() {
        int size = vectorStoreCache.size();
        vectorStoreCache.clear();
        log.info("🗑️ Cleared {} vector store caches", size);
    }

    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        return String.format("Cached vector stores: %d [%s]",
                vectorStoreCache.size(),
                String.join(", ", vectorStoreCache.keySet()));
    }

    /**
     * Check if a concept is cached
     */
    public boolean isCached(String concept) {
        return vectorStoreCache.containsKey(concept.toUpperCase());
    }
}