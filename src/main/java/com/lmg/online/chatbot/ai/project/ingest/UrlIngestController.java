package com.lmg.online.chatbot.ai.project.ingest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin REST API for ingesting content into the Redis vector store.
 *
 * All endpoints live under /api/admin/vector/ingest.
 * Protected at network/gateway level like other /api/admin/** endpoints.
 *
 * ─────────────────────────────────────────────────────────
 * Option C — URL ingest (async, Jsoup HTML extraction)
 * ─────────────────────────────────────────────────────────
 *   POST   /api/admin/vector/ingest
 *          Fetches the URL, strips nav/header/footer noise via Jsoup,
 *          extracts content via selector or smart fallback, chunks and stores.
 *          Returns immediately with jobId — poll status endpoint.
 *
 *   GET    /api/admin/vector/ingest/{jobId}    — poll job status
 *   GET    /api/admin/vector/ingest            — list all jobs (?concept= filter)
 *
 * ─────────────────────────────────────────────────────────
 * Option D — Direct text ingest (synchronous)
 * ─────────────────────────────────────────────────────────
 *   POST   /api/admin/vector/ingest/text
 *          Paste clean text directly (ideal for JS-rendered pages).
 *          Processes synchronously and returns chunk count immediately.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/vector/ingest")
@RequiredArgsConstructor
@Tag(name = "Vector Ingestion", description = "Admin: ingest web/text content into Redis vector store")
public class UrlIngestController {

    private final UrlIngestService ingestService;

    // ────────────────────────────────────────────────────────────────────────
    // Option C — URL ingest (async)
    // ────────────────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(
            summary = "Submit a URL for ingestion (async)",
            description = """
                    Fetches the page HTML, strips all nav/header/footer/ad noise with Jsoup,
                    extracts the main content (using your CSS selector or auto-detection),
                    chunks the text, embeds with OpenAI and stores in the concept-specific Redis vector store.

                    Returns HTTP 202 immediately with a jobId.
                    Poll GET /{jobId} to check PENDING → PROCESSING → DONE | FAILED.

                    Note: if the page is JS-rendered (React/Angular SPA), content will be empty.
                    Use POST /text instead for those pages.

                    Returns HTTP 409 if the URL was already successfully ingested.
                    """
    )
    public ResponseEntity<UrlIngestJobView> submit(@Valid @RequestBody UrlIngestRequest request) {
        log.info("📥 URL ingest request — url: {}, concept: {}, selector: '{}'",
                request.getUrl(), request.getConcept(),
                request.getSelector() != null ? request.getSelector() : "auto");
        UrlIngestJob job = ingestService.submit(request);
        return ResponseEntity.accepted().body(UrlIngestJobView.from(job));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Poll the status of a URL ingestion job")
    public ResponseEntity<UrlIngestJobView> getJob(@PathVariable String jobId) {
        return ResponseEntity.ok(UrlIngestJobView.from(ingestService.getJob(jobId)));
    }

    @GetMapping
    @Operation(
            summary = "List all URL ingestion jobs",
            description = "Pass ?concept=LIFESTYLE to filter by brand. Ordered newest-first."
    )
    public ResponseEntity<List<UrlIngestJobView>> listJobs(
            @RequestParam(required = false) String concept) {
        List<UrlIngestJobView> jobs = ingestService.listJobs(concept)
                .stream()
                .map(UrlIngestJobView::from)
                .toList();
        return ResponseEntity.ok(jobs);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Option D — Direct text ingest (synchronous)
    // ────────────────────────────────────────────────────────────────────────

    @PostMapping("/text")
    @Operation(
            summary = "Ingest raw text directly (synchronous)",
            description = """
                    Ideal for JS-rendered pages (React/Angular SPAs) where URL fetching cannot get content.

                    Steps:
                      1. Open the page in your browser
                      2. Select all visible text (Cmd+A or Ctrl+A), copy it
                      3. Paste into the 'text' field of this request

                    Processing is synchronous — response includes chunksStored immediately.
                    No dedup check (same text can be ingested multiple times if needed).
                    """
    )
    public ResponseEntity<Map<String, Object>> ingestText(
            @Valid @RequestBody TextIngestRequest request) {
        log.info("📥 Text ingest request — concept: {}, category: '{}', title: '{}', chars: {}",
                request.getConcept(), request.getCategory(),
                request.getTitle(), request.getText().length());

        int chunksStored = ingestService.ingestText(request);

        return ResponseEntity.ok(Map.of(
                "status",       "DONE",
                "concept",      request.getConcept().toUpperCase(),
                "title",        request.getTitle()    != null ? request.getTitle()    : "manual-input",
                "category",     request.getCategory() != null ? request.getCategory() : "web",
                "chunksStored", chunksStored,
                "ingestedAt",   Instant.now().toString()
        ));
    }

    // ────────────────────────────────────────────────────────────────────────
    // Exception handlers
    // ────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(UrlIngestService.DuplicateIngestException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(
            UrlIngestService.DuplicateIngestException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status",    409,
                "error",     "Conflict",
                "message",   ex.getMessage(),
                "hint",      "URL already ingested. Use POST /text to re-ingest different content for this URL.",
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(UrlIngestService.JobNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            UrlIngestService.JobNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status",    404,
                "error",     "Not Found",
                "message",   ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "status",    400,
                "error",     "Bad Request",
                "message",   ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // ────────────────────────────────────────────────────────────────────────
    // Response view
    // ────────────────────────────────────────────────────────────────────────

    public record UrlIngestJobView(
            String  jobId,
            String  url,
            String  concept,
            String  selector,
            String  title,
            String  category,
            String  status,
            Integer chunksStored,
            String  errorMessage,
            String  createdAt,
            String  updatedAt
    ) {
        static UrlIngestJobView from(UrlIngestJob j) {
            return new UrlIngestJobView(
                    j.getJobId(),
                    j.getUrl(),
                    j.getConcept(),
                    j.getSelector(),
                    j.getTitle(),
                    j.getCategory(),
                    j.getStatus().name(),
                    j.getChunksStored(),
                    j.getErrorMessage(),
                    j.getCreatedAt() != null ? j.getCreatedAt().toString() : null,
                    j.getUpdatedAt() != null ? j.getUpdatedAt().toString() : null
            );
        }
    }
}
