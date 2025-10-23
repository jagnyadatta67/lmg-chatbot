package com.lmg.online.chatbot.ai.project.doc.vector;


import com.lmg.online.chatbot.ai.project.doc.vector.config.VectorStoreFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-Tenant PDF Upload Service
 * Handles concept-specific policy document uploads
 */
@Service
@Slf4j
public class MultiTenantPdfService {

    private final VectorStoreFactory vectorStoreFactory;
    private final VectorStore orderVectorStore;
    private final TokenTextSplitter textSplitter;

    // Supported concepts
    private static final Set<String> VALID_CONCEPTS = Set.of(
            "MAX", "LIFESTYLE", "BABYSHOP","HOMECENTRE"
    );

    public MultiTenantPdfService(
            VectorStoreFactory vectorStoreFactory,
            VectorStore orderVectorStore) {
        this.vectorStoreFactory = vectorStoreFactory;
        this.orderVectorStore = orderVectorStore;
        this.textSplitter = new TokenTextSplitter(800, 200, 5, 10000, true);

        // Pre-initialize all concept vector stores
        vectorStoreFactory.initializeAllConcepts();
    }

    /**
     * Upload concept-specific policy PDF
     *
     * @param file PDF file
     * @param concept Website concept (MAX, CENTREPOINT, SHOEMART)
     * @param category Document category (return_policy, shipping_policy, etc.)
     * @param metadata Additional metadata
     * @return Document ID
     */
    public String uploadPolicyPdf(
            MultipartFile file,
            String concept,
            String category,
            Map<String, Object> metadata) throws IOException {

        // Validate concept
        if (!isValidConcept(concept)) {
            throw new IllegalArgumentException(
                    "Invalid concept. Must be one of: " + VALID_CONCEPTS);
        }

        log.info("📄 Uploading policy PDF for concept: {} (category: {}, file: {})",
                concept, category, file.getOriginalFilename());

        // Get concept-specific vector store
        VectorStore conceptVectorStore = vectorStoreFactory.getVectorStore(concept);

        // Process and upload
        return uploadToVectorStore(file, conceptVectorStore, concept, category, metadata);
    }

    /**
     * Upload shared order documentation (optional - for order FAQs)
     *
     * @param file PDF file
     * @param category Document category
     * @return Document ID
     */
    public String uploadSharedOrderPdf(
            MultipartFile file,
            String category) throws IOException {

        log.info("📄 Uploading shared order PDF (category: {}, file: {})",
                category, file.getOriginalFilename());

        return uploadToVectorStore(file, orderVectorStore, "SHARED", category, null);
    }

    /**
     * Core upload logic
     */
    private String uploadToVectorStore(
            MultipartFile file,
            VectorStore vectorStore,
            String concept,
            String category,
            Map<String, Object> metadata) throws IOException {

        // Validate
        validateFile(file);

        String documentId = UUID.randomUUID().toString();

        try {
            // Read PDF
            byte[] pdfBytes = file.getBytes();
            ByteArrayResource resource = new ByteArrayResource(pdfBytes);
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            List<Document> documents = pdfReader.get();

            log.info("📖 Extracted {} pages from PDF", documents.size());

            // Build metadata
            Map<String, Object> enrichedMetadata = new HashMap<>();
            enrichedMetadata.put("document_id", documentId);
            enrichedMetadata.put("concept", concept);
            enrichedMetadata.put("filename", file.getOriginalFilename());
            enrichedMetadata.put("category", category);
            enrichedMetadata.put("upload_timestamp", String.valueOf(System.currentTimeMillis()));
            enrichedMetadata.put("file_size", String.valueOf(file.getSize()));

            if (metadata != null) {
                enrichedMetadata.putAll(metadata);
            }

            // Add metadata to documents
            documents.forEach(doc -> doc.getMetadata().putAll(enrichedMetadata));

            // Split into chunks
            List<Document> chunks = textSplitter.apply(documents);
            log.info("✂️ Split into {} chunks", chunks.size());

            // Store in concept-specific collection
            vectorStore.add(chunks);
            log.info("✅ Added {} chunks to vector store for concept: {}",
                    chunks.size(), concept);

            return documentId;

        } catch (Exception e) {
            log.error("❌ Error processing PDF for concept {}: {}", concept, e.getMessage(), e);
            throw new RuntimeException("Failed to process PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Delete document from concept-specific store
     */
    public void deletePolicyDocument(String concept, String documentId) {
        if (!isValidConcept(concept)) {
            throw new IllegalArgumentException("Invalid concept: " + concept);
        }

        VectorStore conceptVectorStore = vectorStoreFactory.getVectorStore(concept);
        conceptVectorStore.delete(List.of(documentId));
        log.info("🗑️ Deleted document {} from concept: {}", documentId, concept);
    }

    /**
     * Search concept-specific policies
     */
    public List<Document> searchPolicies(String concept, String query, int topK) {
        if (!isValidConcept(concept)) {
            throw new IllegalArgumentException("Invalid concept: " + concept);
        }

        VectorStore conceptVectorStore = vectorStoreFactory.getVectorStore(concept);
        return conceptVectorStore.similaritySearch(query);
    }

    /**
     * Get statistics per concept
     */
    public Map<String, ConceptStats> getAllConceptStats() {
        Map<String, ConceptStats> stats = new HashMap<>();

        for (String concept : VALID_CONCEPTS) {
            try {
                VectorStore store = vectorStoreFactory.getVectorStore(concept);
                // In real implementation, query Qdrant for collection stats
                stats.put(concept, new ConceptStats(concept, 0, 0));
            } catch (Exception e) {
                log.error("Error getting stats for concept: {}", concept, e);
            }
        }

        return stats;
    }

    /**
     * Clear all documents for a concept (use with caution!)
     */
    public void clearConceptDocuments(String concept) {
        if (!isValidConcept(concept)) {
            throw new IllegalArgumentException("Invalid concept: " + concept);
        }

        try {
            log.warn("⚠️ DANGER: Clearing ALL documents for concept: {}", concept);

            // Get the vector store for the concept
            VectorStore store = vectorStoreFactory.getVectorStore(concept);

            // Get all document IDs first
            List<String> allDocIds = getAllDocumentIds(concept);

            if (allDocIds.isEmpty()) {
                log.info("ℹ️ No documents to delete for {}", concept);
                return;
            }

            log.info("Found {} documents to delete from {}", allDocIds.size(), concept);

            // Delete all documents using the delete() method
            store.delete(allDocIds);

            log.info("✅ Successfully cleared {} documents from {}",
                    allDocIds.size(), concept);

        } catch (Exception e) {
            log.error("❌ Failed to clear documents for concept: {}", concept, e);
            throw new RuntimeException("Failed to clear documents for " + concept, e);
        }
    }

    // Get all document IDs for a concept
    private List<String> getAllDocumentIds(String concept) {
        try {
            VectorStore store = vectorStoreFactory.getVectorStore(concept);

            // Use similarity search with a generic query to get all documents
            // Set topK to a high number to retrieve all documents
            SearchRequest searchRequest = SearchRequest.builder().query("").topK(10000).build();

            List<Document> allDocs = store.similaritySearch(searchRequest);

            List<String> docIds = allDocs.stream()
                    .map(Document::getId)
                    .filter(id -> id != null && !id.isEmpty())
                    .collect(Collectors.toList());

            log.debug("Retrieved {} document IDs from {}", docIds.size(), concept);
            return docIds;

        } catch (Exception e) {
            log.error("❌ Failed to get document IDs for {}", concept, e);
            return Collections.emptyList();
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported");
        }

        if (file.getSize() > 10 * 1024 * 1024) { // 10MB
            throw new IllegalArgumentException("File size exceeds 10MB limit");
        }
    }





    private boolean isValidConcept(String concept) {
        return concept != null && VALID_CONCEPTS.contains(concept.toUpperCase());
    }

    public record ConceptStats(
            String concept,
            int documentCount,
            long totalChunks
    ) {}
}