package com.lmg.online.chatbot.ai.project.ingest;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Tracks the lifecycle of a URL ingestion job.
 *
 * Dedup strategy: before ingesting a URL, we check if a DONE job already
 * exists for the same urlHash (SHA-256 of the normalised URL).
 * Only one DONE entry per URL is allowed; re-ingest is rejected with 409.
 *
 * Table: url_ingest_jobs
 */
@Entity
@Table(
        name = "url_ingest_jobs",
        indexes = {
                @Index(name = "idx_ingest_job_id",   columnList = "jobId",   unique = true),
                @Index(name = "idx_ingest_url_hash", columnList = "urlHash"),
                @Index(name = "idx_ingest_concept",  columnList = "concept")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UrlIngestJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID assigned at creation — returned to caller as the async handle. */
    @Column(nullable = false, unique = true, length = 36)
    private String jobId;

    /** The original URL supplied by the admin. */
    @Column(nullable = false, length = 2000)
    private String url;

    /** SHA-256 hex of url.trim().toLowerCase() — used for dedup. */
    @Column(nullable = false, length = 64)
    private String urlHash;

    /** Brand concept this content belongs to (e.g. LIFESTYLE, MAX). */
    @Column(nullable = false, length = 50)
    private String concept;

    /** Optional CSS selector used to target specific content on the page. */
    @Column(length = 200)
    private String selector;

    /** Human-readable label for the content (e.g. "Shipping Policy"). */
    @Column(length = 200)
    private String title;

    /** Content category tag (e.g. "shipping", "returns"). Defaults to "web". */
    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    /** Number of text chunks stored in Redis after successful ingestion. */
    @Column
    private Integer chunksStored;

    /** Populated when status = FAILED. */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum JobStatus {
        PENDING,
        PROCESSING,
        DONE,
        FAILED
    }
}
