package com.lmg.online.chatbot.ai.common;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import org.springframework.web.util.UriComponentsBuilder;

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


    /**
     * Returns just the raw phone number for a concept.
     * e.g. "1800-123-1555"
     */
    public static String getRawPhoneNumber(String concept) {
        String normalized = concept == null ? "" : concept.trim().toUpperCase();
        return conceptContactMap.getOrDefault(normalized, "1800-123-1555");
    }

    /**
     * Returns a customer support line sentence.
     * e.g. "For assistance, please call our customer support at 1800-123-1555."
     */
    public static String getPhoneNumber(String concept) {
        return "For assistance, please call our customer support at " + getRawPhoneNumber(concept) + ".";
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


    /**
     * 🔹 Build getTokenDetails URL with safe single-encoding of the token.
     *
     * The token may arrive already URL-encoded (e.g. from a browser data-* attribute).
     * The existing {@link #buildApiUrl} passes values straight to {@link #encode},
     * which would double-encode an already-encoded token (%2F → %252F).
     *
     * This method normalises the token — decode first if needed, then encode exactly
     * once — without touching any existing method.
     *
     * Example result:
     *   https://uat1.lifestylestores.com/landmarkshopscommercews/v2/lifestylein
     *   /chatbot/getTokenDetails?appId=Desktop&token=w24%2FU2l2v...%3D%3D
     *
     * @param concept  brand code  – LIFESTYLE / MAX / BABYSHOP / HOMECENTRE
     * @param env      environment – uat1, uat5, prod (null = prod / www)
     * @param token    raw or already-URL-encoded user token
     * @param appId    client app type – Desktop / Mobile / ANDROID / IPHONE
     * @return fully constructed, correctly encoded URL
     */
    /**
     * 🔹 Build getTokenDetails URL using {@link UriComponentsBuilder}.
     *
     * UriComponentsBuilder.queryParam() handles encoding internally, so the token
     * is always single-encoded regardless of whether it arrives raw or pre-encoded.
     * The token is decoded first (if already percent-encoded) so UriComponentsBuilder
     * encodes it exactly once.
     *
     * Example result:
     *   https://uat1.lifestylestores.com/landmarkshopscommercews/v2/lifestylein
     *   /chatbot/getTokenDetails?appId=Desktop&token=w24%2FU2l2v...%3D%3D
     *
     * @param concept  brand code  – LIFESTYLE / MAX / BABYSHOP / HOMECENTRE
     * @param env      environment – uat1, uat5, prod (null = prod / www)
     * @param token    raw or already-URL-encoded user token
     * @param appId    client app type – Desktop / Mobile / ANDROID / IPHONE
     * @return fully constructed, correctly single-encoded URL string
     */
    public static String buildTokenDetailsUrl(String concept, String env,
                                              String token,   String appId) {
        validateInputs(concept, "chatbot/getTokenDetails");

        String baseUrl = getEnvBaseUrl(concept, env);
        String siteId  = getSiteId(concept);

        String apiPath = buildPath(
                baseUrl,
                "landmarkshopscommercews/v2",
                siteId,
                "chatbot/getTokenDetails"
        );

        // Decode first so UriComponentsBuilder encodes exactly once,
        // even if the token arrived already percent-encoded.
        String decodedToken;
        try {
            decodedToken = URLDecoder.decode(token, StandardCharsets.UTF_8);
        } catch (Exception e) {
            decodedToken = token; // not valid percent-encoded — use as-is
        }

        return UriComponentsBuilder
                .fromHttpUrl(apiPath)
                .queryParam("appId", appId)
                .queryParam("token", decodedToken)
                .toUriString();
    }

    /**
     * 🔹 Same as {@link #buildTokenDetailsUrl} but returns a {@link URI} object.
     *
     * Use this when calling via RestTemplate — passing a {@code URI} prevents
     * RestTemplate from re-encoding already percent-encoded characters
     * (e.g. {@code %3D} → {@code %253D}).
     *
     * The token is decoded first then encoded once by UriComponentsBuilder,
     * and {@code .build().toUri()} locks in the encoding so RestTemplate
     * never touches it again.
     */
    public static URI buildTokenDetailsUri(String concept, String env,
                                           String token,   String appId) {
        validateInputs(concept, "chatbot/getTokenDetails");

        String baseUrl = getEnvBaseUrl(concept, env);
        String siteId  = getSiteId(concept);

        String apiPath = buildPath(
                baseUrl,
                "landmarkshopscommercews/v2",
                siteId,
                "chatbot/getTokenDetails"
        );

        String decodedToken;
        try {
            decodedToken = URLDecoder.decode(token, StandardCharsets.UTF_8);
        } catch (Exception e) {
            decodedToken = token;
        }

        return UriComponentsBuilder
                .fromHttpUrl(apiPath)
                .queryParam("appId", appId)
                .queryParam("token", decodedToken)
                .build()       // encode=false — values are raw, builder will encode once
                .toUri();      // returns URI — RestTemplate will NOT re-encode
    }

    /** 🔹 Build Token URL: https://<env>.<domain>/landmarkshopscommercews/in/oauth/token */
    public static String buildTokenUrl(String concept, String env) {
        String baseUrl = getEnvBaseUrl(concept, env);

        return buildPath(
                baseUrl,
                "landmarkshopscommercews",
                "in",
                "oauth",
                "token"
        );
    }

    public static String buildProfileApiUrl(String concept, String env, String appId) {
        validateInputs(concept, "/en/users/current/profile");
        String baseUrl = getEnvBaseUrl(concept, env);
        String siteId  = getSiteId(concept);

        String path = buildPath(
                baseUrl,
                "landmarkshopscommercews/v3",
                siteId,
                "/en/users/current/profile"
        );

        return path + "?appId=" + encode(appId == null ? "Desktop" : appId);
    }
}
