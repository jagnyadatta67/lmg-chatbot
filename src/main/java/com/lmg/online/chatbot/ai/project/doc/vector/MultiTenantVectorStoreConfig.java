package com.lmg.online.chatbot.ai.project.doc.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import com.lmg.online.chatbot.ai.project.doc.vector.config.MultiTenantVectorService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MultiTenantVectorStoreConfig {

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String host;

    @Value("${spring.ai.vectorstore.qdrant.port:6334}")
    private int port;

    @Value("${spring.ai.vectorstore.qdrant.api-key:}")
    private String apiKey;

    @Value("${spring.ai.vectorstore.qdrant.use-tls:false}")
    private boolean useTls;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:chatbot_documents}")
    private String defaultCollectionName;
    @Autowired
    private MultiTenantVectorService multiTenantVectorService;

    /**
     * Create Qdrant Client Bean
     */
    @Bean
    public QdrantClient qdrantClient() {
        log.info("🔗 Creating Qdrant client for {}:{} (TLS: {})", host, port, useTls);

        QdrantGrpcClient.Builder grpcBuilder = QdrantGrpcClient.newBuilder(host, port, useTls);

        // Add API key if provided (for cloud)
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            grpcBuilder.withApiKey(apiKey);
            log.info("🔐 Using API key authentication");
        } else {
            log.info("🔓 No API key configured (local mode)");
        }

        QdrantClient client = new QdrantClient(grpcBuilder.build());
        log.info("✅ Qdrant client created successfully");
        return client;
    }

    /**
     * Shared default vector store (optional fallback)
     */
    @Bean
    public QdrantVectorStore qdrantVectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        log.info("🔗 Creating default vector store with collection: {}", defaultCollectionName);

        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(defaultCollectionName)
                .initializeSchema(true)
                .build();
    }

    /**
     * Factory bean to create tenant-specific vector stores
     */
    @Bean
    public VectorStoreFactory vectorStoreFactory(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        return new VectorStoreFactory(qdrantClient, embeddingModel);
    }

    /**
     * Shared Order Vector Store (common collection)
     */
    @Bean("orderVectorStore")
    public VectorStore orderVectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        log.info("🧩 Initializing shared order vector store");

        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName("chatbot_orders_shared")
                .initializeSchema(true)
                .build();
    }

    /**
     * Factory to create and manage concept-specific vector stores dynamically
     */
    public static class VectorStoreFactory {

        private final QdrantClient qdrantClient;
        private final EmbeddingModel embeddingModel;
        private final Map<String, VectorStore> vectorStoreCache = new ConcurrentHashMap<>();

        public VectorStoreFactory(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
            this.qdrantClient = qdrantClient;
            this.embeddingModel = embeddingModel;
            log.info("🏭 VectorStoreFactory initialized");
        }

        /**
         * Get or create concept-specific vector store
         *
         * @param concept Website concept (MAX, CENTREPOINT, SHOEMART)
         * @return Vector store for that concept
         */
        public VectorStore getVectorStore(String concept) {
            String normalizedConcept = concept.toUpperCase();
            return vectorStoreCache.computeIfAbsent(normalizedConcept, c -> {
                String collectionName = "chatbot_policy_" + c.toLowerCase();
                log.info("🔧 Creating vector store for concept: {} (collection: {})", c, collectionName);

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
            String[] concepts = {"MAX", "LIFESTYLE", "BABYSHOP","HOMECENTRE"};
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



}