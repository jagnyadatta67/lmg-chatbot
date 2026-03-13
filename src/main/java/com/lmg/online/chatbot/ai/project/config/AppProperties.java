package com.lmg.online.chatbot.ai.project.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Centralised, type-safe configuration for the Landmark Chatbot.
 *
 * All values are bound from the "app.*" namespace in application.properties
 * (or the active profile override).  Secrets arrive via environment variables
 * — no hardcoded credentials in source.
 *
 * Usage:
 *   @Autowired AppProperties props;
 *   props.getSecurity().getApiKeyHeader();   // e.g. "X-API-Key"
 *   props.getBusiness().getCache().getTtlHours();
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid
    private SecurityProperties security = new SecurityProperties();

    @Valid
    private BusinessProperties business = new BusinessProperties();

    // ----------------------------------------------------------------
    // Security
    // ----------------------------------------------------------------

    @Getter
    @Setter
    public static class SecurityProperties {

        /**
         * Name of the HTTP header that clients must use to pass their API key.
         * Default: X-API-Key
         *
         * NOTE: The actual API key value is no longer stored in properties —
         * all keys are managed in the chatbot_clients database table.
         */
        private String apiKeyHeader = "X-API-Key";
    }

    // ----------------------------------------------------------------
    // Business
    // ----------------------------------------------------------------

    @Getter
    @Setter
    public static class BusinessProperties {

        /** Maximum tokens allowed per LLM response. */
        @Min(1)
        private int maxTokens = 1024;

        /** Default concept/brand used when none is provided in the request. */
        private String defaultConcept = "lmg";

        @Valid
        private CacheProperties cache = new CacheProperties();

        @Valid
        private RateLimitProperties rateLimit = new RateLimitProperties();

        @Valid
        private FeatureFlags features = new FeatureFlags();
    }

    // ----------------------------------------------------------------
    // Cache
    // ----------------------------------------------------------------

    @Getter
    @Setter
    public static class CacheProperties {

        /** How long entries stay in the Caffeine cache (hours). */
        @Min(1)
        private int ttlHours = 1;

        /** Maximum number of entries in the Caffeine cache. */
        @Min(100)
        private int maxSize = 1000;
    }

    // ----------------------------------------------------------------
    // Rate Limiting
    // ----------------------------------------------------------------

    @Getter
    @Setter
    public static class RateLimitProperties {

        /** Maximum requests per minute per client (API key). */
        @Min(1)
        private int requestsPerMinute = 60;
    }

    // ----------------------------------------------------------------
    // Feature Flags
    // ----------------------------------------------------------------

    @Getter
    @Setter
    public static class FeatureFlags {

        /** Persist AI usage analytics to MySQL. */
        private boolean analyticsEnabled = true;

        /** Enable Caffeine response caching. */
        private boolean cacheEnabled = true;

        /** Enable Qdrant/Redis semantic vector search. */
        private boolean vectorSearchEnabled = true;
    }
}
