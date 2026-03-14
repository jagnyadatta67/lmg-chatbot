package com.lmg.online.chatbot.ai.project.ingest;



import com.lmg.online.chatbot.ai.project.doc.vector.config.chroma.VectorStoreFactoryRedis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.document.Document.Builder;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Async processor that runs URL ingestion in a background thread.
 *
 * Kept as a separate Spring bean so that @Async is honoured
 * (Spring AOP proxies do not intercept self-calls on the same bean).
 *
 * HTML extraction pipeline (Option C):
 *  1. Fetch raw HTML via RestTemplate
 *  2. Strip all noise elements (nav, header, footer, scripts, ads, etc.)
 *  3. Extract content — either via admin-provided CSS selector or smart fallback chain
 *  4. Split clean text into chunks with TokenTextSplitter
 *  5. Attach metadata and store in concept-specific Redis vector store
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlIngestAsyncProcessor {

    private final UrlIngestJobRepository jobRepository;
    private final VectorStoreFactoryRedis vectorStoreFactory;
    private final RestTemplate restTemplate;

    private static final int CHUNK_SIZE      = 500;
    private static final int CHUNK_OVERLAP   = 50;
    private static final int MIN_CHUNK_CHARS = 50;

    /**
     * Tags and CSS selectors that are pure noise — removed before content extraction.
     * Order matters: tag-level removal happens first, then class/attribute-based.
     */
    private static final String NOISE_SELECTOR =
            "nav, header, footer, aside, " +
            "script, style, noscript, iframe, " +
            "form, button, input, select, textarea, " +
            "[role=navigation], [role=banner], [role=contentinfo], [role=complementary], " +
            ".nav, .navbar, .navigation, .menu, .mega-menu, " +
            ".header, .site-header, .top-header, .page-header, " +
            ".footer, .site-footer, .page-footer, " +
            ".breadcrumb, .breadcrumbs, " +
            ".cookie, .cookie-banner, .cookie-consent, " +
            ".social, .social-media, .social-links, " +
            ".advertisement, .ad, .ads, .promo, .banner, " +
            ".sidebar, .widget, .related, " +
            ".pagination, .pager";

    /**
     * Fallback content selector chain — tried in order when no selector is provided.
     * First non-empty match wins.
     */
    private static final String[] CONTENT_FALLBACK_CHAIN = {
            "main",
            "article",
            "[role=main]",
            "#main-content",
            "#content",
            "#main",
            ".main-content",
            ".page-content",
            ".content-area",
            ".content"
    };

    @Async("ingestExecutor")
    @Transactional
    public void process(String jobId) {
        UrlIngestJob job = jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

        log.info("⚙️  [{}] Starting ingestion — url: {}, selector: '{}'",
                jobId, job.getUrl(), job.getSelector() != null ? job.getSelector() : "auto");

        job.setStatus(UrlIngestJob.JobStatus.PROCESSING);
        jobRepository.save(job);

        try {
            // ── Step 1: Fetch raw HTML ────────────────────────────────────────
            String html = fetchHtml(job.getUrl());
            log.info("📡 [{}] Fetched {} bytes of HTML", jobId, html.length());

            // ── Step 2: Parse and strip noise ────────────────────────────────
            Document doc = Jsoup.parse(html, job.getUrl());
            String pageTitle = doc.title();
            doc.select(NOISE_SELECTOR).remove();
            log.info("🧹 [{}] Stripped noise elements. Page title: '{}'", jobId, pageTitle);

            // ── Step 3: Extract content ───────────────────────────────────────
            String cleanText = extractContent(doc, job.getSelector(), jobId);

            if (cleanText.isBlank()) {
                throw new RuntimeException(
                        "No content extracted from URL. " +
                        "The page may be JS-rendered. Try Option D (text ingest) instead, " +
                        "or provide a more specific CSS selector.");
            }
            log.info("📄 [{}] Extracted {} characters of clean text", jobId, cleanText.length());

            // ── Step 4: Split into chunks ─────────────────────────────────────
            org.springframework.ai.document.Document rawDoc =
                    new org.springframework.ai.document.Document(cleanText);

            TokenTextSplitter splitter = new TokenTextSplitter(
                    CHUNK_SIZE, CHUNK_OVERLAP, MIN_CHUNK_CHARS, 10000, true);
            List<org.springframework.ai.document.Document> chunks = splitter.apply(List.of(rawDoc));
            log.info("✂️  [{}] Split into {} chunks", jobId, chunks.size());

            // ── Step 5: Attach metadata ───────────────────────────────────────
            String effectiveTitle    = job.getTitle()    != null ? job.getTitle()    : pageTitle;
            String effectiveCategory = job.getCategory() != null ? job.getCategory() : "web";
            long   uploadEpoch       = Instant.now().toEpochMilli();

            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> meta = chunks.get(i).getMetadata();
                meta.put("concept",    job.getConcept().toUpperCase());
                meta.put("category",   effectiveCategory);
                meta.put("policyType", effectiveTitle);
                meta.put("uploadDate", uploadEpoch);
                meta.put("url",        job.getUrl());
                meta.put("urlHash",    job.getUrlHash());
                meta.put("chunkIndex", i);
            }

            // ── Step 6: Store in Redis ────────────────────────────────────────
            VectorStore vectorStore = vectorStoreFactory.getVectorStore(job.getConcept());
            vectorStore.add(chunks);
            log.info("💾 [{}] Stored {} chunks in Redis for concept '{}'",
                    jobId, chunks.size(), job.getConcept());

            // ── Step 7: Mark DONE ─────────────────────────────────────────────
            job.setStatus(UrlIngestJob.JobStatus.DONE);
            job.setChunksStored(chunks.size());
            jobRepository.save(job);
            log.info("✅ [{}] Ingestion complete — {} chunks stored", jobId, chunks.size());

        } catch (Exception e) {
            log.error("❌ [{}] Ingestion failed: {}", jobId, e.getMessage(), e);
            job.setStatus(UrlIngestJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fetches raw HTML from the given URL using the configured RestTemplate.
     * Sets a browser-like User-Agent to avoid bot-detection rejections.
     */
    private String fetchHtml(String url) {
        try {
            // Use Jsoup's built-in fetcher with browser headers — avoids 403s
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; LandmarkChatbot/1.0)")
                    .timeout(15_000)
                    .ignoreContentType(false)
                    .get()
                    .outerHtml();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch URL: " + url + " — " + e.getMessage(), e);
        }
    }

    /**
     * Extracts clean text from the already-noise-stripped Jsoup document.
     *
     * Strategy:
     *  - If admin provided a CSS selector → use it exclusively
     *  - Otherwise → walk through CONTENT_FALLBACK_CHAIN, return first match
     *  - Final fallback → full body text (already stripped of noise)
     */
    private String extractContent(Document doc, String selector, String jobId) {
        // Admin-specified selector takes priority
        if (selector != null && !selector.isBlank()) {
            Elements selected = doc.select(selector.trim());
            if (selected.isEmpty()) {
                log.warn("⚠️  [{}] Selector '{}' matched no elements — falling back to auto", jobId, selector);
            } else {
                String text = selected.text().trim();
                log.info("🎯 [{}] Used selector '{}' → {} chars", jobId, selector, text.length());
                return text;
            }
        }

        // Smart fallback chain
        for (String candidate : CONTENT_FALLBACK_CHAIN) {
            Element el = doc.selectFirst(candidate);
            if (el != null) {
                String text = el.text().trim();
                if (!text.isBlank()) {
                    log.info("🎯 [{}] Auto-selected '{}' → {} chars", jobId, candidate, text.length());
                    return text;
                }
            }
        }

        // Last resort — full body (noise already removed)
        String bodyText = doc.body() != null ? doc.body().text().trim() : "";
        log.info("⚠️  [{}] Using full body fallback → {} chars", jobId, bodyText.length());
        return bodyText;
    }
}
