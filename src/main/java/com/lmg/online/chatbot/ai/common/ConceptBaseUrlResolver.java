package com.lmg.online.chatbot.ai.common;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public class ConceptBaseUrlResolver {

    private static final Map<String, String> BASE_URLS = Map.of(
            "LIFESTYLE", "https://www.lifestylestores.com",
            "MAX", "https://www.maxfashion.in",
            "BABYSHOP", "https://www.babyshop.in",
            "HOMECENTRE", "https://www.homecentre.in"
    );

    private static final Map<String, String> SITE_IDS = Map.of(
            "LIFESTYLE", "lifestylein",
            "MAX", "maxin",
            "BABYSHOP", "babyshopin",
            "HOMECENTRE", "homecentrein"
    );

    public static   Map<String, String> conceptContactMap = Map.ofEntries(
            Map.entry("MAX", "1800-123-1444"),
            Map.entry("HOMECENTRE", "1800-212-7500"),
            Map.entry("BABYSHOP", "1800-123-7467"),
            Map.entry("LIFESTYLE", "1800-123-1555")
    );


    public static String getPhoneNumber(String concept) {

        String phoneNumber=conceptContactMap.get(concept);

        return "If unclear,: 'Call"+ phoneNumber+" for details.";

    }

    // === PUBLIC ENTRY METHODS ===

    /** 🔹 Build API URL with appId only */
    public static String buildApiUrl(String concept, String env, String uriPath, String appId) {
        return buildApiUrl(concept, env, uriPath, appId, null);
    }

    /** 🔹 Build API URL with query parameters */
    public static String buildApiUrl(String concept, String env, String uriPath, String appId, Map<String, String> queryParams) {
        validateInputs(concept, uriPath);
        String baseUrl = getEnvBaseUrl(concept, env);
        String siteId = getSiteId(concept);

        String apiPath = buildPath(
                baseUrl,
                "landmarkshopscommercews/v2",
                siteId,
                uriPath
        );

        StringJoiner paramJoiner = new StringJoiner("&", "?",
                queryParams == null || queryParams.isEmpty() ? "" : "");
        paramJoiner.add("appId=" + encode(appId));

        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach((k, v) -> paramJoiner.add(encode(k) + "=" + encode(v)));
        }

        return apiPath + paramJoiner;
    }

    /** 🔹 Build React (UI) Page URL (e.g., PDP, PLP, etc.) */
    public static String buildReactUrl(String concept, String env, String uriPath) {
        validateInputs(concept, uriPath);
        String baseUrl = getEnvBaseUrl(concept, env);
        return buildPath(baseUrl, "in/en", uriPath);
    }

    /** 🔹 Build Authentication/Root URL */
    public static String buildAuthUrl(String concept, String env) {
        return getEnvBaseUrl(concept, env);
    }

    // === HELPER METHODS ===

    /** Resolve concept base URL */
    private static String getBaseUrl(String conceptCode) {
        String url = BASE_URLS.get(normalize(conceptCode));
        if (url == null)
            throw new IllegalArgumentException("❌ No base URL configured for concept: " + conceptCode);
        return url;
    }

    /** Replace "www." with environment prefix (e.g., uat5.) */
    private static String getEnvBaseUrl(String concept, String env) {
        String baseUrl = getBaseUrl(concept);
        if (isBlank(env)) return baseUrl;
        return baseUrl.replace("www.", env.trim() + ".");
    }

    /** Get site ID (used in API paths) */
    private static String getSiteId(String concept) {
        String siteId = SITE_IDS.get(normalize(concept));
        if (siteId == null)
            throw new IllegalArgumentException("❌ No site ID configured for concept: " + concept);
        return siteId;
    }

    /** Join path parts safely, avoiding double slashes */
    private static String buildPath(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (isBlank(part)) continue;
            String clean = part.replaceAll("^/+", "").replaceAll("/+$", "");
            sb.append("/").append(clean);
        }
        return sb.toString().replaceFirst("^/", ""); // ensure no leading double slash
    }

    /** Validate inputs */
    private static void validateInputs(String concept, String uriPath) {
        if (isBlank(concept))
            throw new IllegalArgumentException("Concept code cannot be null or empty");
        if (isBlank(uriPath))
            throw new IllegalArgumentException("URI path cannot be null or empty");
    }

    /** Normalize to uppercase key */
    private static String normalize(String concept) {
        return concept.trim().toUpperCase();
    }

    /** URL encode safely */
    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Null or blank check */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
