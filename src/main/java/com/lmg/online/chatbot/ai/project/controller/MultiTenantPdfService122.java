package com.lmg.online.chatbot.ai.project.controller;



import com.lmg.online.chatbot.ai.project.doc.vector.config.VectorStoreFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Optimized Multi-Tenant PDF Service
 * Handles concept-specific document uploads, retrieval, and deletion.
 */
@Service
@Slf4j
public class MultiTenantPdfService122 {

    private final VectorStoreFactory vectorStoreFactory;
    private final TokenTextSplitter textSplitter;

    // Supported concepts
    private static final Set<String> VALID_CONCEPTS = Set.of("MAX", "LIFESTYLE", "BABYSHOP", "HOMECENTRE");

    public MultiTenantPdfService122(VectorStoreFactory vectorStoreFactory) {
        this.vectorStoreFactory = vectorStoreFactory;
        this.textSplitter = new TokenTextSplitter(800, 200, 5, 10000, true);
        vectorStoreFactory.initializeAllConcepts();
    }

    // ====================================================================================
    // 1️⃣ Upload concept-specific PDF
    // ====================================================================================
    public String uploadPolicyPdf(MultipartFile file, String concept, String category, Map<String, Object> metadata)
            throws IOException {

        validateConcept(concept);
        validateFile(file);

        log.info("📄 Uploading PDF for concept [{}], category [{}], file [{}]",
                concept, category, file.getOriginalFilename());

        VectorStore store = vectorStoreFactory.getVectorStore(concept);
        String documentId = UUID.randomUUID().toString();

        try {
            // Read and split PDF
            var pdfReader = new PagePdfDocumentReader(new ByteArrayResource(file.getBytes()));
            List<Document> docs = pdfReader.get();

            // Enrich metadata
            Map<String, Object> meta = new HashMap<>(Optional.ofNullable(metadata).orElse(Map.of()));
            meta.put("document_id", documentId);
            meta.put("concept", concept);
            meta.put("filename", file.getOriginalFilename());
            meta.put("category", category);
            meta.put("upload_timestamp", String.valueOf(System.currentTimeMillis()));
            meta.put("file_size", String.valueOf(file.getSize()));

            docs.forEach(d -> d.getMetadata().putAll(meta));

            List<Document> chunks = textSplitter.apply(docs);
            store.add(chunks);

            log.info("✅ Uploaded [{}] chunks for [{}]", chunks.size(), concept);
            return documentId;

        } catch (Exception e) {
            log.error("❌ Upload failed for {}: {}", concept, e.getMessage(), e);
            throw new RuntimeException("Failed to upload PDF: " + e.getMessage(), e);
        }
    }

    // ====================================================================================
    // 2️⃣ Get all document IDs grouped by concept
    // ====================================================================================
    public Map<String, List<String>> getAllDocumentIdsByConcept() {
        Map<String, List<String>> result = new HashMap<>();

        for (String concept : VALID_CONCEPTS) {
            try {
                List<String> ids = getAllDocumentIds(concept);
                result.put(concept, ids);
            } catch (Exception e) {
                log.error("❌ Error fetching document IDs for {}: {}", concept, e.getMessage());
                result.put(concept, List.of());
            }
        }

        log.info("📦 Retrieved document IDs for {} concepts", result.size());
        return result;
    }

    // ====================================================================================
    // 3️⃣ Delete a specific document
    // ====================================================================================
    public void deletePolicyDocument(String concept, String documentId) {
        validateConcept(concept);

        try {
            VectorStore store = vectorStoreFactory.getVectorStore(concept);
            store.delete(List.of(documentId));
            log.info("🗑️ Deleted document [{}] for concept [{}]", documentId, concept);
        } catch (Exception e) {
            log.error("❌ Failed to delete document [{}] for [{}]: {}", documentId, concept, e.getMessage(), e);
            throw new RuntimeException("Failed to delete document: " + documentId, e);
        }
    }

    // ====================================================================================
    // 4️⃣ Delete all documents for a concept
    // ====================================================================================
    public void clearConceptDocuments(String concept) {
        validateConcept(concept);

        try {
            VectorStore store = vectorStoreFactory.getVectorStore(concept);
            List<String> ids = getAllDocumentIds(concept);

            if (ids.isEmpty()) {
                log.info("ℹ️ No documents to delete for [{}]", concept);
                return;
            }

            store.delete(ids);
            log.info("✅ Cleared {} documents for concept [{}]", ids.size(), concept);

        } catch (Exception e) {
            log.error("❌ Failed to clear documents for {}: {}", concept, e.getMessage(), e);
            throw new RuntimeException("Failed to clear documents for " + concept, e);
        }
    }

    // ====================================================================================
    // 🔍 Helper: Fetch all document IDs from Vector Store
    // ====================================================================================
    private List<String> getAllDocumentIds(String concept) {
        validateConcept(concept);

        try {
            VectorStore store = vectorStoreFactory.getVectorStore(concept);

            SearchRequest searchRequest = SearchRequest.builder()
                    .query("concept search")
                    .filterExpression("concept == '" + concept + "'")
                    .topK(10000)
                    .build();

            List<Document> docs = store.similaritySearch(searchRequest);
            return docs.stream()
                    .map(Document::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Could not fetch document IDs for {}: {}", concept, e.getMessage());
            return List.of();
        }
    }

    // ====================================================================================
    // 🧰 Validation Helpers
    // ====================================================================================
    private void validateConcept(String concept) {
        if (concept == null || !VALID_CONCEPTS.contains(concept.toUpperCase())) {
            throw new IllegalArgumentException("Invalid concept. Must be one of: " + VALID_CONCEPTS);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");
        if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf"))
            throw new IllegalArgumentException("Only PDF files are supported");
        if (file.getSize() > 10 * 1024 * 1024)
            throw new IllegalArgumentException("File size exceeds 10MB limit");
    }
}
