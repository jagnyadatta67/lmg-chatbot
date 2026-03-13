package com.lmg.online.chatbot.ai.project.ingest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UrlIngestJobRepository extends JpaRepository<UrlIngestJob, Long> {

    Optional<UrlIngestJob> findByJobId(String jobId);

    /** Used for dedup — check if this URL was already ingested successfully. */
    boolean existsByUrlHashAndStatus(String urlHash, UrlIngestJob.JobStatus status);

    List<UrlIngestJob> findByConceptOrderByCreatedAtDesc(String concept);

    List<UrlIngestJob> findAllByOrderByCreatedAtDesc();
}
