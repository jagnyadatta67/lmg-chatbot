package com.lmg.online.chatbot.ai.project.doc.vector.config;


import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


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
     * Shared Order Vector Store (common collection)
     */
    @Bean("orderVectorStoreRedis")
    public VectorStore orderVectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        log.info("🧩 Initializing shared order vector store");

        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName("chatbot_orders_shared")
                .initializeSchema(true)
                .build();
    }

    /**
     * Initialize all concepts on application startup
     */
    @Bean
    public String initializeVectorStores(VectorStoreFactory vectorStoreFactory) {
        vectorStoreFactory.initializeAllConcepts();
        return "Vector stores initialized";
    }
}