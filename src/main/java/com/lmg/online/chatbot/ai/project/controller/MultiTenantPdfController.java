package com.lmg.online.chatbot.ai.project.controller;



import com.lmg.online.chatbot.ai.project.doc.vector.MultiTenantPdfService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Multi-Tenant PDF Upload Controller
 * Allows business users to upload concept-specific policy documents
 *
 * Examples:
 * - Upload MAX return policy: POST /api/admin/documents/MAX/upload
 * - Upload Centrepoint shipping policy: POST /api/admin/documents/CENTREPOINT/upload
 * - Upload Shoemart exchange policy: POST /api/admin/documents/SHOEMART/upload
 */

@RequiredArgsConstructor
@Slf4j
public class MultiTenantPdfController {

    private final MultiTenantPdfService pdfService;

    /**
     * Upload concept-specific policy PDF
     *
     * curl -X POST http://localhost:8080/api/admin/documents/MAX/upload \
     *   -F "file=@max_return_policy.pdf" \
     *   -F "category=return_policy" \
     *   -F "version=2.0"
     */
    @PostMapping("/{concept}/upload")
    public ResponseEntity<UploadResponse> uploadPolicyDocument(
            @PathVariable String concept,
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "author", required = false) String author,
            @RequestParam(value = "effectiveDate", required = false) String effectiveDate) {

        try {
            log.info("📤 Upload request - Concept: {}, File: {}, Category: {}",
                    concept, file.getOriginalFilename(), category);

            // Build metadata
            Map<String, Object> metadata = new HashMap<>();
            if (version != null) metadata.put("version", version);
            if (author != null) metadata.put("author", author);
            if (effectiveDate != null) metadata.put("effective_date", effectiveDate);

            // Upload to concept-specific vector store
            String documentId = pdfService.uploadPolicyPdf(file, concept, category, metadata);

            return ResponseEntity.ok(new UploadResponse(
                    true,
                    "Policy document uploaded successfully for " + concept,
                    documentId,
                    concept,
                    file.getOriginalFilename(),
                    category
            ));

        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new UploadResponse(false, e.getMessage(), null, null, null, null));

        } catch (Exception e) {
            log.error("❌ Error uploading document: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UploadResponse(false, "Failed to upload document", null, null, null, null));
        }
    }

    /**
     * Upload shared order documentation (optional)
     *
     * curl -X POST http://localhost:8080/api/admin/documents/shared/upload \
     *   -F "file=@order_faq.pdf" \
     *   -F "category=order_faq"
     */
    @PostMapping("/shared/upload")
    public ResponseEntity<UploadResponse> uploadSharedDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "author", required = false) String author) {

        try {
            log.info("📤 Shared upload request - File: {}, Category: {}",
                    file.getOriginalFilename(), category);

            // Upload to shared order vector store
            String documentId = pdfService.uploadSharedOrderPdf(file, category);

            return ResponseEntity.ok(new UploadResponse(
                    true,
                    "Shared document uploaded successfully",
                    documentId,
                    "SHARED",
                    file.getOriginalFilename(),
                    category
            ));

        } catch (Exception e) {
            log.error("❌ Error uploading shared document: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UploadResponse(false, "Failed to upload document", null, null, null, null));
        }
    }

    /**
     * Delete concept-specific document by ID
     *
     * curl -X DELETE http://localhost:8080/api/admin/documents/MAX/{documentId}
     */
    @DeleteMapping("/{concept}/{documentId}")
    public ResponseEntity<DeleteResponse> deleteDocument(
            @PathVariable String concept,
            @PathVariable String documentId) {

        try {
            pdfService.deletePolicyDocument(concept, documentId);

            return ResponseEntity.ok(new DeleteResponse(
                    true,
                    "Document deleted successfully from " + concept
            ));

        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Invalid concept: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new DeleteResponse(false, e.getMessage()));

        } catch (Exception e) {
            log.error("❌ Error deleting document: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DeleteResponse(false, "Failed to delete document"));
        }
    }

    /**
     * Search concept-specific policies
     *
     * curl -X GET "http://localhost:8080/api/admin/documents/MAX/search?query=return+policy&limit=5"
     */
    @GetMapping("/{concept}/search")
    public ResponseEntity<SearchResponse> searchPolicies(
            @PathVariable String concept,
            @RequestParam("query") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {

        try {
            var documents = pdfService.searchPolicies(concept, query, limit);

            return ResponseEntity.ok(new SearchResponse(
                    true,
                    "Found " + documents.size() + " documents",
                    concept,
                    documents.size(),
                    documents
            ));

        } catch (Exception e) {
            log.error("❌ Error searching documents: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new SearchResponse(false, "Search failed", concept, 0, null));
        }
    }

    /**
     * Get statistics for all concepts
     *
     * curl http://localhost:8080/api/admin/documents/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, MultiTenantPdfService.ConceptStats>> getAllStats() {
        try {
            return ResponseEntity.ok(pdfService.getAllConceptStats());
        } catch (Exception e) {
            log.error("❌ Error fetching stats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get statistics for specific concept
     *
     * curl http://localhost:8080/api/admin/documents/MAX/stats
     */
    @GetMapping("/{concept}/stats")
    public ResponseEntity<ConceptStatsResponse> getConceptStats(@PathVariable String concept) {
        try {
            var allStats = pdfService.getAllConceptStats();
            var conceptStats = allStats.get(concept.toUpperCase());

            if (conceptStats == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(new ConceptStatsResponse(
                    true,
                    "Statistics for " + concept,
                    conceptStats
            ));

        } catch (Exception e) {
            log.error("❌ Error fetching concept stats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Clear all documents for a concept (DANGER!)
     *
     * curl -X DELETE http://localhost:8080/api/admin/documents/MAX/clear
     */
    @DeleteMapping("/{concept}/clear")
    public ResponseEntity<DeleteResponse> clearConceptDocuments(@PathVariable String concept) {
        try {
            log.warn("⚠️ CLEARING ALL DOCUMENTS for concept: {}", concept);
            pdfService.clearConceptDocuments(concept);

            return ResponseEntity.ok(new DeleteResponse(
                    true,
                    "All documents cleared for " + concept
            ));

        } catch (Exception e) {
            log.error("❌ Error clearing documents: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DeleteResponse(false, "Failed to clear documents"));
        }
    }

    /**
     * Health check
     *
     * curl http://localhost:8080/api/admin/documents/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "multi-tenant-pdf-upload",
                "supported_concepts", "MAX, LIFESTYLE, HOMECENTRE, BABYSHOP"
        ));
    }

    /**
     * List supported concepts
     *
     * curl http://localhost:8080/api/admin/documents/concepts
     */
    @GetMapping("/concepts")
    public ResponseEntity<ConceptsResponse> getSupportedConcepts() {
        return ResponseEntity.ok(new ConceptsResponse(
                true,
                "Supported concepts",
                java.util.List.of("MAX", "BABYSHOP", "LIFESTYLE","HOMECENTRE","BABYSHOP")
        ));
    }

    // ========== Response DTOs ==========

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

    public record SearchResponse(
            boolean success,
            String message,
            String concept,
            int resultCount,
            Object documents
    ) {}

    public record ConceptStatsResponse(
            boolean success,
            String message,
            MultiTenantPdfService.ConceptStats stats
    ) {}

    public record ConceptsResponse(
            boolean success,
            String message,
            java.util.List<String> concepts
    ) {}
}