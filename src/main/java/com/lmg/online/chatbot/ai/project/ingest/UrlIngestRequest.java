package com.lmg.online.chatbot.ai.project.ingest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UrlIngestRequest {

    /**
     * The web page URL to fetch and ingest.
     * Must be http or https.
     */
    @NotBlank(message = "url must not be blank")
    @Size(max = 2000, message = "url must not exceed 2000 characters")
    @Pattern(
            regexp = "^https?://.*",
            message = "url must start with http:// or https://"
    )
    private String url;

    /**
     * Brand concept this content belongs to.
     * Supported values: LIFESTYLE, MAX, BABYSHOP, HOMECENTRE.
     */
    @NotBlank(message = "concept must not be blank")
    @Size(max = 50, message = "concept must not exceed 50 characters")
    private String concept;

    /**
     * Optional CSS selector to target a specific content area on the page.
     *
     * Examples:
     *   "main"              → standard HTML5 main element
     *   ".help-content"     → a specific content div by class
     *   "#policy-container" → a specific div by ID
     *   "article"           → article element
     *
     * If omitted, the extractor tries common content selectors in order:
     *   main → article → [role=main] → #content → .content → body (fallback)
     */
    @Size(max = 200, message = "selector must not exceed 200 characters")
    private String selector;

    /**
     * Optional label for this content (e.g. "Shipping Policy").
     * Stored as metadata on every chunk — helps with filtering and debugging.
     */
    @Size(max = 200, message = "title must not exceed 200 characters")
    private String title;

    /**
     * Optional category tag (e.g. "shipping", "returns", "exchange").
     * Stored as metadata — can be used to filter vector search by topic.
     * Defaults to "web" if omitted.
     */
    @Size(max = 100, message = "category must not exceed 100 characters")
    private String category;
}
