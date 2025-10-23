package com.lmg.online.chatbot.ai.project.doc.vector.config;


import io.qdrant.client.QdrantClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing multi-tenant vector collections
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiTenantVectorService {

    private final QdrantClient qdrantClient;

    /**
     * Ensure Qdrant collection exists before creating vector store
     */
    public void ensureCollectionExists(String collectionName) {
        try {
            // Simple approach: Try to create collection and handle "already exists" case
            qdrantClient.createCollectionAsync(collectionName,
                    io.qdrant.client.grpc.Collections.VectorParams.newBuilder()
                            .setSize(1536) // OpenAI embeddings size
                            .setDistance(io.qdrant.client.grpc.Collections.Distance.Cosine)
                            .build()
            ).get();
            log.info("✅ Collection created: {}", collectionName);
        } catch (Exception e) {
            // If collection already exists, that's fine - just log it
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.debug("📁 Collection already exists: {}", collectionName);
            } else {
                log.warn("⚠️ Collection operation for '{}': {}", collectionName, e.getMessage());
            }
        }
    }

    /**
     * Check if a collection exists
     */
    public boolean collectionExists(String collectionName) {
        try {
            qdrantClient.getCollectionInfoAsync(collectionName).get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Delete a collection (use with caution)
     */
    public void deleteCollection(String collectionName) {
        try {
            qdrantClient.deleteCollectionAsync(collectionName).get();
            log.info("🗑️ Collection deleted: {}", collectionName);
        } catch (Exception e) {
            log.error("❌ Failed to delete collection '{}': {}", collectionName, e.getMessage());
        }
    }

    /**
     * Get collection statistics
     */
    public void printCollectionStats(String collectionName) {
        try {
            var collectionInfo = qdrantClient.getCollectionInfoAsync(collectionName).get();
            log.info("📊 Collection '{}' stats: {}", collectionName, collectionInfo);
        } catch (Exception e) {
            log.warn("Cannot get stats for collection '{}': {}", collectionName, e.getMessage());
        }
    }
}