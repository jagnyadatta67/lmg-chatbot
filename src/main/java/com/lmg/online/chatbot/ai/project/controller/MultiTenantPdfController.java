package com.lmg.online.chatbot.ai.project.controller;


import com.lmg.online.chatbot.ai.project.doc.vector.config.chroma.VectorStoreFactoryRedis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Multi-Tenant PDF Controller
 *
 * Endpoints:
 * 1. POST   /api/admin/documents/{concept}/upload     → Upload PDF → embed → store in Redis
 * 2. GET    /api/admin/documents/all                  → List all document IDs grouped by concept
 * 3. DELETE /api/admin/documents/{concept}            → Delete all documents for a concept
 * 4. DELETE /api/admin/documents/{concept}/{docId}    → Delete a specific document
 * 5. POST   /api/admin/documents/init                 → Pre-warm all concept vector store indexes
 */
@RestController
@RequestMapping("/api/admin/documents")
@RequiredArgsConstructor
@Slf4j
public class MultiTenantPdfController {

    private final MultiTenantPdfService    pdfService;
    private final VectorStoreFactoryRedis vectorStoreFactory;

    // ── 1. Upload PDF ─────────────────────────────────────────────────────────

    /**
     * Upload a concept-specific policy PDF.
     *
     * curl -X POST http://localhost:8080/api/admin/documents/MAX/upload \
     *   -F "file=@max_policy.pdf" \
     *   -F "category=return_policy"
     */
    @PostMapping("/{concept}/upload")
    public ResponseEntity<UploadResponse> uploadPolicyDocument(
            @PathVariable String concept,
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "author",  required = false) String author) {

        try {
            log.info("📤 Upload request — concept: {}, file: {}, category: {}",
                    concept, file.getOriginalFilename(), category);

            Map<String, Object> metadata = new HashMap<>();
            if (version != null) metadata.put("version", version);
            if (author  != null) metadata.put("author",  author);

            String documentId = pdfService.uploadPolicyPdf(file, concept, category, metadata);

            return ResponseEntity.ok(new UploadResponse(
                    true,
                    "Policy uploaded successfully for " + concept,
                    documentId,
                    concept,
                    file.getOriginalFilename(),
                    category
            ));
        } catch (Exception e) {
            log.error("❌ Upload failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UploadResponse(false, "Upload failed: " + e.getMessage(),
                            null, concept, null, category));
        }
    }

    // ── 2. List all documents ─────────────────────────────────────────────────

    /**
     * Returns all document IDs grouped by concept.
     *
     * curl http://localhost:8080/api/admin/documents/all
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, List<String>>> getAllDocuments() {
        try {
            return ResponseEntity.ok(pdfService.getAllDocumentIdsByConcept());
        } catch (Exception e) {
            log.error("❌ Error fetching document list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ── 3. Delete all for a concept ───────────────────────────────────────────

    /**
     * Deletes all ingested documents for a concept from the Redis vector store.
     *
     * curl -X DELETE http://localhost:8080/api/admin/documents/MAX
     */
    @DeleteMapping("/{concept}")
    public ResponseEntity<DeleteResponse> deleteAllConceptDocuments(@PathVariable String concept) {
        try {
            pdfService.clearConceptDocuments(concept);
            return ResponseEntity.ok(new DeleteResponse(true, "All documents deleted for " + concept));
        } catch (Exception e) {
            log.error("❌ Failed to delete all documents for {}: {}", concept, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DeleteResponse(false, "Failed to delete documents: " + e.getMessage()));
        }
    }

    // ── 4. Delete single document ─────────────────────────────────────────────

    /**
     * Deletes a specific document by ID.
     *
     * curl -X DELETE http://localhost:8080/api/admin/documents/MAX/MAX_policy.pdf
     */
    @DeleteMapping("/{concept}/{documentId}")
    public ResponseEntity<DeleteResponse> deleteDocument(
            @PathVariable String concept,
            @PathVariable String documentId) {
        try {
            pdfService.deletePolicyDocument(concept, documentId);
            return ResponseEntity.ok(new DeleteResponse(true, "Document deleted: " + documentId));
        } catch (Exception e) {
            log.error("❌ Failed to delete document {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DeleteResponse(false, "Failed to delete document: " + e.getMessage()));
        }
    }

    // ── 5. Init / warm-up ─────────────────────────────────────────────────────

    /**
     * Pre-warms all concept vector store indexes.
     * Safe to call at any time — idempotent.
     *
     * curl -X POST http://localhost:8080/api/admin/documents/init
     */
    @PostMapping("/init")
    public ResponseEntity<DeleteResponse> initAllConcepts() {
        try {
            vectorStoreFactory.initializeAllConcepts();
            return ResponseEntity.ok(new DeleteResponse(true, "All concept indexes initialized"));
        } catch (Exception e) {
            log.error("❌ Init failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DeleteResponse(false, "Init failed: " + e.getMessage()));
        }
    }

    // ── Response records ──────────────────────────────────────────────────────

    public record UploadResponse(
            boolean success,
            String  message,
            String  documentId,
            String  concept,
            String  filename,
            String  category
    ) {}

    public record DeleteResponse(
            boolean success,
            String  message
    ) {}
}
