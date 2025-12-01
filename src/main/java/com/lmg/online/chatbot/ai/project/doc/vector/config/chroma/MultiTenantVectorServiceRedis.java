package com.lmg.online.chatbot.ai.project.doc.vector.config.chroma;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.IndexDefinition;
import redis.clients.jedis.search.IndexOptions;
import redis.clients.jedis.search.Schema;

import java.util.Set;

/**
 * Service for managing multi-tenant Redis vector indexes
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiTenantVectorServiceRedis {

    private final JedisPooled jedisPooled;

    /**
     * Ensure Redis index exists before creating vector store
     * Note: RedisVectorStore with initializeSchema=true handles this automatically
     */
    public void ensureIndexExists(String indexName) {
        try {
            if (indexExists(indexName)) {
                log.debug("📁 Index already exists: {}", indexName);
            } else {
                log.info("📝 Index will be created on first use: {}", indexName);
            }
        } catch (Exception e) {
            log.debug("Index check for '{}': {}", indexName, e.getMessage());
        }
    }

    /**
     * Check if an index exists
     */
    public boolean indexExists(String indexName) {
        try {
            jedisPooled.ftInfo(indexName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Delete an index (use with caution)
     */
    public void deleteIndex(String indexName) {
        try {
            jedisPooled.ftDropIndex(indexName);
            log.info("🗑️ Index deleted: {}", indexName);
        } catch (Exception e) {
            log.error("❌ Failed to delete index '{}': {}", indexName, e.getMessage());
        }
    }

    /**
     * Delete index and all associated documents
     */
    public void deleteIndexAndDocuments(String indexName) {
        try {
            // Drop index and delete documents
            jedisPooled.ftDropIndex(indexName);
            log.info("🗑️ Index and documents deleted: {}", indexName);
        } catch (Exception e) {
            log.error("❌ Failed to delete index and docs '{}': {}", indexName, e.getMessage());
        }
    }

    /**
     * Get index statistics
     */
    public void printIndexStats(String indexName) {
        try {
            if (indexExists(indexName)) {
                var info = jedisPooled.ftInfo(indexName);
                log.info("📊 Index '{}' stats: {}", indexName, info);
            } else {
                log.warn("Index '{}' does not exist", indexName);
            }
        } catch (Exception e) {
            log.warn("Cannot get stats for index '{}': {}", indexName, e.getMessage());
        }
    }

    /**
     * List all Redis Search indexes
     */
    public void listIndexes() {
        try {
            Set<String> indexes = jedisPooled.ftList();
            log.info("📚 Available indexes: {}", indexes);
        } catch (Exception e) {
            log.error("❌ Failed to list indexes: {}", e.getMessage());
        }
    }

    /**
     * Count documents in an index
     */
    public long getDocumentCount(String indexName) {
        try {
            if (indexExists(indexName)) {
                var info = jedisPooled.ftInfo(indexName);
                // Extract document count from info
                Object numDocs = info.get("num_docs");
                if (numDocs != null) {
                    return Long.parseLong(numDocs.toString());
                }
            }
            return 0;
        } catch (Exception e) {
            log.warn("Cannot get count for index '{}': {}", indexName, e.getMessage());
            return 0;
        }
    }

    /**
     * Delete documents by prefix
     */
    public void deleteDocumentsByPrefix(String prefix) {
        try {
            Set<String> keys = jedisPooled.keys(prefix + "*");
            if (!keys.isEmpty()) {
                jedisPooled.del(keys.toArray(new String[0]));
                log.info("🗑️ Deleted {} documents with prefix: {}", keys.size(), prefix);
            } else {
                log.info("No documents found with prefix: {}", prefix);
            }
        } catch (Exception e) {
            log.error("❌ Failed to delete documents with prefix '{}': {}", prefix, e.getMessage());
        }
    }

    /**
     * Check Redis connection health
     */
    public boolean isHealthy() {
        try {
            String pong = jedisPooled.ping();
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            log.error("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get Redis info
     */
    public void printRedisInfo() {
        try {
            String info = jedisPooled.info();
            log.info("📊 Redis Info:\n{}", info);
        } catch (Exception e) {
            log.error("❌ Failed to get Redis info: {}", e.getMessage());
        }
    }

    /**
     * Flush all data (DANGEROUS - use only in development)
     */
    public void flushAll() {
        try {
            jedisPooled.flushAll();
            log.warn("⚠️ All Redis data flushed!");
        } catch (Exception e) {
            log.error("❌ Failed to flush Redis: {}", e.getMessage());
        }
    }


}