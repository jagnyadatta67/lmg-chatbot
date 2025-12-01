package com.lmg.online.chatbot.ai.project.doc.vector.config.chroma;

import com.lmg.online.chatbot.ai.project.doc.vector.config.VectorStoreFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
@Slf4j
public class MultiTenantVectorStoreConfigRedis {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.ai.vectorstore.redis.initialize-schema:true}")
    private boolean initializeSchema;

    @Value("${spring.ai.vectorstore.redis.index-name:chatbot-documents-idx}")
    private String defaultIndexName;

    @Value("${spring.ai.vectorstore.redis.prefix:chatbot:doc:}")
    private String defaultPrefix;

    /**
     * Create JedisPooled Bean (Redis connection)
     */
    @Bean
    public JedisPooled jedisPooled() {
        log.info("🔗 Creating Redis connection");
        log.info("   Host: {}:{}", redisHost, redisPort);

        JedisPooled jedis;

        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            log.info("🔐 Using password authentication");
            jedis = new JedisPooled(redisHost, redisPort, null, redisPassword);
        } else {
            log.info("🔓 No password configured");
            jedis = new JedisPooled(redisHost, redisPort);
        }

        // Test connection
        try {
            String pong = jedis.ping();
            log.info("✅ Redis connection successful: {}", pong);
        } catch (Exception e) {
            log.error("❌ Redis connection failed: {}", e.getMessage());
        }

        return jedis;
    }

    /**
     * Shared default vector store (optional fallback)
     */
    @Bean
    public RedisVectorStore redisVectorStore(
            JedisPooled jedisPooled,
            EmbeddingModel embeddingModel) {

        log.info("🔗 Creating default Redis vector store");
        log.info("   Index: {}", defaultIndexName);
        log.info("   Prefix: {}", defaultPrefix);

        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(defaultIndexName)
                .prefix(defaultPrefix)
                .initializeSchema(initializeSchema)
                .metadataFields(
                        MetadataField.tag("concept"),
                        MetadataField.tag("category"),
                        MetadataField.tag("source"),
                        MetadataField.numeric("uploadDate")
                )
                .batchingStrategy(new TokenCountBatchingStrategy())
                .build();
    }

    /**
     * Shared Order Vector Store (common collection)
     */
    @Bean("orderVectorStoreRedis")
    public VectorStore orderVectorStore(
            JedisPooled jedisPooled,
            EmbeddingModel embeddingModel) {

        log.info("🧩 Initializing shared order vector store");

        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("chatbot-orders-idx")
                .prefix("chatbot:order:")
                .initializeSchema(initializeSchema)
                .metadataFields(
                        MetadataField.tag("orderId"),
                        MetadataField.tag("customerId"),
                        MetadataField.tag("status"),
                        MetadataField.numeric("orderDate")
                )
                .batchingStrategy(new TokenCountBatchingStrategy())
                .build();
    }

    /**
     * Initialize all concepts on application startup
     */
    @Bean
    public String initializeVectorStoresRedis(VectorStoreFactoryRedis vectorStoreFactory) {
        vectorStoreFactory.initializeAllConcepts();
        return "Vector stores initialized";
    }
}