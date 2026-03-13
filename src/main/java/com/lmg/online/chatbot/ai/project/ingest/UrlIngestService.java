package com.lmg.online.chatbot.ai.project.ingest;

import com.lmg.online.chatbot.ai.project.client.ClientAuthService;
import com.lmg.online.chatbot.ai.project.doc.vector.config.chroma.VectorStoreFactoryRedis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates all vector ingestion paths:
 *
 *  • URL ingest  (Option C) — async: dedup → job created → Jsoup fetch + clean → Redis
 *  • Text ingest (Option D) — sync:  split pasted text directly → Redis, returns immediately
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlIngestService {

    private final UrlIngestJobRepository  jobRepository;
    private final UrlIngestAsyncProcessor asyncProcessor;
    private final VectorStoreFactoryRedis vectorStoreFactory;

    private static final int CHUNK_SIZE      = 500;
    private static final int CHUNK_OVERLAP   = 50;
    private static final int MIN_CHUNK_CHARS = 50;

    // ────────────────────────────────────────────────────────────────────────
    // Option C — URL ingest (async)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Submits a URL for background ingestion.
     *
     * @return PENDING job — poll GET /{jobId} for status updates
     * @throws DuplicateIngestException if the URL was already successfully ingested
     */
    @Transactional
    public UrlIngestJob submit(UrlIngestRequest request) {
        String normalised = request.getUrl().trim().toLowerCase();
        String urlHash    = ClientAuthService.sha256Hex(normalised);
        String concept    = request.getConcept().trim().toUpperCase();

        // Dedup — skip if a successful job already exists for this URL
        if (jobRepository.existsByUrlHashAndStatus(urlHash, UrlIngestJob.JobStatus.DONE)) {
            log.info("⏭️  URL already ingested (DONE), skipping: {}", request.getUrl());
            throw new DuplicateIngestException(request.getUrl());
        }

        UrlIngestJob job = UrlIngestJob.builder()
                .jobId(UUID.randomUUID().toString())
                .url(request.getUrl().trim())
                .urlHash(urlHash)
                .concept(concept)
                .selector(request.getSelector())
                .title(request.getTitle())
                .category(request.getCategory())
                .status(UrlIngestJob.JobStatus.PENDING)
                .build();

        jobRepository.save(job);
        log.info("📝 Created URL ingest job [{}] — url: {}, concept: {}, selector: '{}'",
                job.getJobId(), job.getUrl(), job.getConcept(),
                job.getSelector() != null ? job.getSelector() : "auto");

        asyncProcessor.process(job.getJobId());
        return job;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Option D — Direct text ingest (synchronous)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Ingests raw text directly into the Redis vector store.
     * No URL fetch, no async — processes immediately and returns chunk count.
     *
     * Use this for JS-rendered pages: copy the visible text from the browser,
     * paste it here. Content is guaranteed clean.
     *
     * @return number of chunks stored
     */
    @Transactional
    public int ingestText(TextIngestRequest request) {
        String concept         = request.getConcept().trim().toUpperCase();
        String effectiveCategory = request.getCategory() != null ? request.getCategory() : "web";
        String effectiveTitle    = request.getTitle()    != null ? request.getTitle()    : "manual-input";
        long   uploadEpoch       = Instant.now().toEpochMilli();

        log.info("📝 Text ingest — concept: {}, category: '{}', title: '{}', chars: {}",
                concept, effectiveCategory, effectiveTitle, request.getText().length());

        // Split into chunks
        Document rawDoc = new Document(request.getText().trim());
        TokenTextSplitter splitter = new TokenTextSplitter(
                CHUNK_SIZE, CHUNK_OVERLAP, MIN_CHUNK_CHARS, 10000, true);
        List<Document> chunks = splitter.apply(List.of(rawDoc));

        log.info("✂️  Split into {} chunks", chunks.size());

        // Attach metadata to every chunk
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = chunks.get(i).getMetadata();
            meta.put("concept",    concept);
            meta.put("category",   effectiveCategory);
            meta.put("policyType", effectiveTitle);
            meta.put("uploadDate", uploadEpoch);
            meta.put("url",        "manual-input");
            meta.put("urlHash",    "manual");
            meta.put("chunkIndex", i);
        }

        // Store in Redis
        VectorStore vectorStore = vectorStoreFactory.getVectorStore(concept);
        vectorStore.add(chunks);

        log.info("✅ Text ingest complete — {} chunks stored for concept '{}'",
                chunks.size(), concept);
        return chunks.size();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Job queries
    // ────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UrlIngestJob getJob(String jobId) {
        return jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    @Transactional(readOnly = true)
    public List<UrlIngestJob> listJobs(String concept) {
        if (concept != null && !concept.isBlank()) {
            return jobRepository.findByConceptOrderByCreatedAtDesc(concept.trim().toUpperCase());
        }
        return jobRepository.findAllByOrderByCreatedAtDesc();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Exceptions
    // ────────────────────────────────────────────────────────────────────────

    public static class DuplicateIngestException extends RuntimeException {
        public DuplicateIngestException(String url) {
            super("URL already ingested successfully: " + url);
        }
    }

    public static class JobNotFoundException extends RuntimeException {
        public JobNotFoundException(String jobId) {
            super("No ingest job found with id: " + jobId);
        }
    }
}
