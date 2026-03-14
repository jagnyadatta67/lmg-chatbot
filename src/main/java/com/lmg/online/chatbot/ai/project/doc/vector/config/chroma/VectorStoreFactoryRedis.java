package com.lmg.online.chatbot.ai.project.doc.vector.config.chroma;

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
 * Factory to create and manage concept-specific Redis vector stores dynamically.
 *
 * Redis index naming:
 *   index  → chatbot-policy-{concept}-idx
 *   prefix → chatbot:policy:{concept}:
 *
 * Stores are cached in-memory; call getVectorStore(concept) from any service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreFactoryRedis {

    private final JedisPooled                   jedisPooled;
    private final EmbeddingModel                embeddingModel;
    private final MultiTenantVectorServiceRedis multiTenantVectorService;

    private final Map<String, VectorStore> vectorStoreCache = new ConcurrentHashMap<>();

    /**
     * Get or create concept-specific vector store.
     *
     * @param concept brand code — MAX | LIFESTYLE | HOMECENTRE | BABYSHOP
     */
    public VectorStore getVectorStore(String concept) {
        String normalised = concept.toUpperCase();
        return vectorStoreCache.computeIfAbsent(normalised, c -> {
            String indexName = "chatbot-policy-" + c.toLowerCase() + "-idx";
            String prefix    = "chatbot:policy:" + c.toLowerCase() + ":";

            log.info("🔧 Creating Redis vector store for concept: {} (index: {})", c, indexName);

            try {
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

                vectorStore.afterPropertiesSet();
                Thread.sleep(100);

                if (multiTenantVectorService.indexExists(indexName)) {
                    log.info("✅ Index created: {}", indexName);
                    multiTenantVectorService.printIndexStats(indexName);
                } else {
                    throw new RuntimeException("Failed to create index: " + indexName);
                }

                return vectorStore;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while creating vector store for " + c, e);
            } catch (Exception e) {
                log.error("❌ Failed to create vector store for {}: {}", c, e.getMessage(), e);
                throw new RuntimeException("Failed to create vector store for " + c, e);
            }
        });
    }

    /** Pre-load vector stores for all known concepts. */
    public void initializeAllConcepts() {
        String[] concepts = {"MAX", "LIFESTYLE", "BABYSHOP", "HOMECENTRE"};
        log.info("🚀 Initializing concept vector stores...");
        int ok = 0;
        for (String concept : concepts) {
            try {
                getVectorStore(concept);
                ok++;
                log.info("✅ [{}/{}] Initialized: {}", ok, concepts.length, concept);
            } catch (Exception e) {
                log.error("❌ Failed to initialize {}: {}", concept, e.getMessage(), e);
            }
        }
        multiTenantVectorService.listIndexes();
        log.info("✅ Pre-initialized {}/{} concept vector stores", ok, concepts.length);
    }

    public Map<String, VectorStore> getAllVectorStores() { return Map.copyOf(vectorStoreCache); }
    public void clearConcept(String concept)            { vectorStoreCache.remove(concept.toUpperCase()); }
    public void clearAll()                              { vectorStoreCache.clear(); }
    public boolean isCached(String concept)             { return vectorStoreCache.containsKey(concept.toUpperCase()); }

    public String getCacheStats() {
        return String.format("Cached stores: %d [%s]",
                vectorStoreCache.size(), String.join(", ", vectorStoreCache.keySet()));
    }

    public Map<String, Long> getDocumentCounts() {
        Map<String, Long> counts = new ConcurrentHashMap<>();
        vectorStoreCache.forEach((concept, store) -> {
            String indexName = "chatbot-policy-" + concept.toLowerCase() + "-idx";
            counts.put(concept, multiTenantVectorService.getDocumentCount(indexName));
        });
        return counts;
    }

    public Map<String, Boolean> getHealthStatus() {
        Map<String, Boolean> health = new ConcurrentHashMap<>();
        vectorStoreCache.forEach((concept, store) -> {
            String indexName = "chatbot-policy-" + concept.toLowerCase() + "-idx";
            health.put(concept, multiTenantVectorService.indexExists(indexName));
        });
        return health;
    }
}
