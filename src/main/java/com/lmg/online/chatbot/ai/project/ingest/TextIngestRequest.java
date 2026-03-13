package com.lmg.online.chatbot.ai.project.ingest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TextIngestRequest {

    /**
     * The raw policy/help text to ingest.
     * Paste the visible text copied from the browser — clean, no HTML tags.
     * Max 200,000 characters (~100 pages of text).
     */
    @NotBlank(message = "text must not be blank")
    @Size(max = 200_000, message = "text must not exceed 200,000 characters")
    private String text;

    /**
     * Brand concept this content belongs to.
     * Supported values: LIFESTYLE, MAX, BABYSHOP, HOMECENTRE.
     */
    @NotBlank(message = "concept must not be blank")
    @Size(max = 50, message = "concept must not exceed 50 characters")
    private String concept;

    /**
     * Human-readable label for this content.
     * Stored as 'policyType' metadata on every chunk.
     * Example: "Shipping Policy", "Return & Exchange Policy"
     */
    @Size(max = 200, message = "title must not exceed 200 characters")
    private String title;

    /**
     * Content category tag stored in chunk metadata.
     * Example: "shipping", "returns", "exchange", "payment"
     * Defaults to "web" if omitted.
     */
    @Size(max = 100, message = "category must not exceed 100 characters")
    private String category;
}
