package com.lmg.online.chatbot.ai.project.doc.vector;

import io.qdrant.client.QdrantClient;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j

public class MultiTenantVectorService {
    @Autowired
    private QdrantClient qdrantClient;

    public void ensureCollectionExists(String collectionName) {
        // First try the simple create approach
        try {
            qdrantClient.createCollectionAsync(collectionName,
                    io.qdrant.client.grpc.Collections.VectorParams.newBuilder()
                            .setSize(1536)
                            .setDistance(io.qdrant.client.grpc.Collections.Distance.Cosine)
                            .build()
            ).get();
            log.info("✅ Collection created: {}", collectionName);
            return;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.debug("📁 Collection exists: {}", collectionName);
                return;
            }
        }

        // If create failed for other reasons, try the info check
        try {
            qdrantClient.getCollectionInfoAsync(collectionName).get();
            log.debug("📁 Collection verified: {}", collectionName);
        } catch (Exception e) {
            log.error("❌ Cannot ensure collection exists: {}", collectionName);
        }
    }
}
