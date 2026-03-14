package com.lmg.online.chatbot.ai.project.controller;

import com.lmg.online.chatbot.ai.project.doc.vector.config.chroma.MultiTenantVectorServiceRedis;

import com.lmg.online.chatbot.ai.project.doc.vector.config.chroma.VectorStoreFactoryRedis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import redis.clients.jedis.JedisPooled;

import java.util.*;

/**
 * Handles PDF ingestion into the Redis vector store and document management.
 *
 * Redis key pattern: chatbot:policy:{concept}:{documentId}
 *
 * Methods:
 *  - uploadPolicyPdf            → extract text via PagePdfDocumentReader → embed → store
 *  - getAllDocumentIdsByConcept  → scan Redis keys by concept prefix
 *  - clearConceptDocuments      → delete all keys for a concept
 *  - deletePolicyDocument       → delete a single document's keys
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiTenantPdfService {

    private final VectorStoreFactoryRedis vectorStoreFactory;
    private final MultiTenantVectorServiceRedis vectorService;
    private final JedisPooled                   jedisPooled;

    private static final String[] CONCEPTS = {"MAX", "LIFESTYLE", "BABYSHOP", "HOMECENTRE"};

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Reads the PDF, splits it into page-level chunks, enriches each chunk
     * with metadata, embeds via OpenAI and stores in the concept Redis index.
     *
     * @return documentId — stable ID built from concept + sanitised filename
     */
    public String uploadPolicyPdf(MultipartFile file,
                                  String concept,
                                  String category,
                                  Map<String, Object> extraMetadata) throws Exception {

        String normalised = concept.toUpperCase();
        String filename   = Objects.requireNonNullElse(file.getOriginalFilename(), "document.pdf");
        String documentId = normalised + "_" + filename.replaceAll("[^a-zA-Z0-9._-]", "_");

        log.info("📄 [PdfService] Reading PDF: {}, concept: {}, category: {}", filename, normalised, category);

        // 1. PDF bytes → Spring AI PagePdfDocumentReader (one Document per page)
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() { return filename; }
        };

        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> pages = reader.get();

        if (pages.isEmpty()) {
            throw new IllegalArgumentException("PDF produced no readable pages: " + filename);
        }
        log.info("📑 [PdfService] Extracted {} pages from {}", pages.size(), filename);

        // 2. Enrich each page document with metadata
        long uploadTs = System.currentTimeMillis();
        List<Document> enriched = new ArrayList<>();
        for (Document page : pages) {
            Map<String, Object> meta = new HashMap<>(page.getMetadata());
            meta.put("concept",    normalised);
            meta.put("category",   category);
            meta.put("policyType", category);
            meta.put("uploadDate", uploadTs);
            meta.put("documentId", documentId);
            meta.put("filename",   filename);
            if (extraMetadata != null) meta.putAll(extraMetadata);

            enriched.add(new Document(page.getText(), meta));
        }

        // 3. Get (or create) concept vector store and embed + store
        VectorStore vectorStore = vectorStoreFactory.getVectorStore(normalised);
        vectorStore.add(enriched);

        log.info("✅ [PdfService] Stored {} chunks for documentId={}", enriched.size(), documentId);
        return documentId;
    }

    // ── List ──────────────────────────────────────────────────────────────────

    /**
     * Scans Redis keys for each known concept and returns document IDs grouped by concept.
     * Key pattern: chatbot:policy:{concept}:*
     */
    public Map<String, List<String>> getAllDocumentIdsByConcept() {
        Map<String, List<String>> result = new LinkedHashMap<>();

        for (String concept : CONCEPTS) {
            String prefix = "chatbot:policy:" + concept.toLowerCase() + ":";
            try {
                Set<String> keys = jedisPooled.keys(prefix + "*");
                List<String> ids = keys.stream()
                        .map(k -> k.startsWith(prefix) ? k.substring(prefix.length()) : k)
                        .distinct()
                        .sorted()
                        .toList();
                result.put(concept, ids);
            } catch (Exception e) {
                log.warn("⚠️ [PdfService] Could not scan keys for {}: {}", concept, e.getMessage());
                result.put(concept, Collections.emptyList());
            }
        }

        return result;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes all Redis keys for a concept (all ingested documents).
     */
    public void clearConceptDocuments(String concept) {
        String prefix = "chatbot:policy:" + concept.toLowerCase() + ":";
        log.info("🗑️ [PdfService] Clearing all documents for concept={}", concept);
        vectorService.deleteDocumentsByPrefix(prefix);
    }

    /**
     * Deletes all chunks of a single document.
     * Pattern: chatbot:policy:{concept}:{documentId}*
     */
    public void deletePolicyDocument(String concept, String documentId) {
        String prefix = "chatbot:policy:" + concept.toLowerCase() + ":" + documentId;
        log.info("🗑️ [PdfService] Deleting documentId={} for concept={}", documentId, concept);
        vectorService.deleteDocumentsByPrefix(prefix);
    }
}
