package com.lmg.online.chatbot.ai.project.controller;




import com.lmg.online.chatbot.ai.project.doc.vector.config.chroma.VectorStoreFactoryRedis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Simplified Multi-Tenant PDF Controller
 *
 * Endpoints:
 * 1. POST   /api/admin/documents/{concept}/upload     → Upload PDF
 * 2. GET    /api/admin/documents/all                  → List all documents by concept
 * 3. DELETE /api/admin/documents/{concept}            → Delete all documents for concept
 * 4. DELETE /api/admin/documents/{concept}/{docId}    → Delete specific document
 */
@RestController
@RequestMapping("/api/admin/documents")
@RequiredArgsConstructor
@Slf4j
public class MultiTenantPdfController1222 {

    private final MultiTenantPdfService122 pdfService;




    /**
     * Upload concept-specific policy PDF
     *
     * Example:
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
            @RequestParam(value = "author", required = false) String author) {

        try {
            log.info("📤 Upload request for concept: {}, file: {}, category: {}",
                    concept, file.getOriginalFilename(), category);

            Map<String, Object> metadata = new HashMap<>();
            if (version != null) metadata.put("version", version);
            if (author != null) metadata.put("author", author);

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
                    .body(new UploadResponse(false, "Upload failed", null, concept, null, category));
        }
    }

    /**
     * Get all document IDs grouped by concept
     *
     * Example:
     * curl http://localhost:8080/api/admin/documents/all
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, List<String>>> getAllDocuments() {
        try {
            Map<String, List<String>> allDocs = pdfService.getAllDocumentIdsByConcept();
            return ResponseEntity.ok(allDocs);
        } catch (Exception e) {
            log.error("❌ Error fetching document list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete all documents for a concept
     *
     * Example:
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
                    .body(new DeleteResponse(false, "Failed to delete documents"));
        }
    }

    /**
     * Delete a specific document by ID
     *
     * Example:
     * curl -X DELETE http://localhost:8080/api/admin/documents/MAX/12345
     */
    @DeleteMapping("/{concept}/{documentId}")
    public ResponseEntity<DeleteResponse> deleteDocument(
            @PathVariable String concept,
            @PathVariable String documentId) {

        try {
            pdfService.deletePolicyDocument(concept, documentId);
            return ResponseEntity.ok(new DeleteResponse(true, "Document deleted successfully"));
        } catch (Exception e) {
            log.error("❌ Failed to delete document: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DeleteResponse(false, "Failed to delete document"));
        }
    }

    @GetMapping("hit")
    public ResponseEntity<String> hit(
            @PathVariable String concept,
            @PathVariable String documentId) {
        vectorStoreFactoryRedis.initializeAllConcepts();
        return ResponseEntity.ok("OK");
    }

    // ====== Response DTOs ======

    public record UploadResponse(
            boolean success,
            String message,
            String documentId,
            String concept,
            String filename,
            String category
    ) {}

    public record DeleteResponse(
            boolean success,
            String message
    ) {}
@Autowired
    private VectorStoreFactoryRedis vectorStoreFactoryRedis;
}
