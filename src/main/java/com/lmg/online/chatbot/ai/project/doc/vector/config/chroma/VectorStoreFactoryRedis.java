package com.lmg.online.chatbot.ai.project.doc.vector.config.chroma;

import com.lmg.online.chatbot.ai.project.doc.vector.config.chroma.MultiTenantVectorServiceRedis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory to create and manage concept-specific Redis vector stores dynamically
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreFactoryRedis {

    private final JedisPooled jedisPooled;
    private final EmbeddingModel embeddingModel;
    private final MultiTenantVectorServiceRedis multiTenantVectorService;

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
            String indexName = "chatbot-policy-" + c.toLowerCase() + "-idx";
            String prefix = "chatbot:policy:" + c.toLowerCase() + ":";

            log.info("🔧 Creating Redis vector store for concept: {} (index: {})", c, indexName);
            log.info("   Prefix: {}", prefix);

            try {
                // Create the vector store with initializeSchema=true
                RedisVectorStore vectorStore = RedisVectorStore.builder(jedisPooled, embeddingModel)
                        .indexName(indexName)
                        .prefix(prefix)
                        .initializeSchema(true)
                        .metadataFields(
                                MetadataField.tag("concept"),
                                MetadataField.tag("category"),
                                MetadataField.tag("policyType"),
                                MetadataField.numeric("uploadDate")
                        )
                        .batchingStrategy(new TokenCountBatchingStrategy())
                        .build();

                // CRITICAL: Force immediate index creation
                vectorStore.afterPropertiesSet();

                // Small delay to ensure Redis processes the index creation
                Thread.sleep(100);

                // Verify index was created
                boolean indexExists = multiTenantVectorService.indexExists(indexName);
                if (indexExists) {
                    log.info("✅ Index created successfully: {}", indexName);
                    multiTenantVectorService.printIndexStats(indexName);
                } else {
                    log.error("❌ Index creation failed for: {}", indexName);
                    throw new RuntimeException("Failed to create index: " + indexName);
                }

                return vectorStore;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("❌ Thread interrupted while creating vector store for {}", c, e);
                throw new RuntimeException("Failed to create vector store for " + c, e);
            } catch (Exception e) {
                log.error("❌ Failed to create vector store for {}: {}", c, e.getMessage(), e);
                throw new RuntimeException("Failed to create vector store for " + c, e);
            }
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

        int successCount = 0;
        for (String concept : concepts) {
            try {
                VectorStore store = getVectorStore(concept);
                String indexName = "chatbot-policy-" + concept.toLowerCase() + "-idx";

                if (multiTenantVectorService.indexExists(indexName)) {
                    successCount++;
                    log.info("✅ [{}/{}] Initialized: {}", successCount, concepts.length, concept);
                } else {
                    log.error("❌ Failed to verify index for: {}", concept);
                }
            } catch (Exception e) {
                log.error("❌ Failed to initialize {}: {}", concept, e.getMessage(), e);
            }
        }

        // List all indexes to verify
        multiTenantVectorService.listIndexes();

        log.info("✅ Pre-initialized {}/{} concept vector stores", successCount, concepts.length);
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

    /**
     * Get health status of all concept stores
     */
    public Map<String, Boolean> getHealthStatus() {
        Map<String, Boolean> healthStatus = new ConcurrentHashMap<>();

        for (String concept : vectorStoreCache.keySet()) {
            String indexName = "chatbot-policy-" + concept.toLowerCase() + "-idx";
            boolean exists = multiTenantVectorService.indexExists(indexName);
            healthStatus.put(concept, exists);
        }

        return healthStatus;
    }

    /**
     * Get document counts for all concepts
     */
    public Map<String, Long> getDocumentCounts() {
        Map<String, Long> counts = new ConcurrentHashMap<>();

        for (String concept : vectorStoreCache.keySet()) {
            String indexName = "chatbot-policy-" + concept.toLowerCase() + "-idx";
            long count = multiTenantVectorService.getDocumentCount(indexName);
            counts.put(concept, count);
        }

        return counts;
    }
}