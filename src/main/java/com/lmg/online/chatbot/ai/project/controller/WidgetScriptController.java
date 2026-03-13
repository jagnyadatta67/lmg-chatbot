package com.lmg.online.chatbot.ai.project.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Serves chatbot-widget.js with domain-based access control.
 *
 * The file lives at src/main/resources/widget/chatbot-widget.js (NOT in /static/),
 * so it is never served automatically by Spring Boot. Only requests whose
 * Origin or Referer header matches a known Landmark brand domain (or localhost
 * for development) will receive the script.
 *
 * Access:  GET /widget/chatbot-widget.js
 * Allowed: *.maxfashion.in, *.lifestylestores.com, *.babyshop.in,
 *          *.homecentre.in, *.landmarkshops.in, localhost, 127.0.0.1
 * Denied:  403 Forbidden
 */
@Slf4j
@RestController
@RequestMapping("/widget")
public class WidgetScriptController {

    private static final List<Pattern> ALLOWED_ORIGINS = List.of(
            Pattern.compile("https?://([a-z0-9-]+\\.)?maxfashion\\.in(:\\d+)?"),
            Pattern.compile("https?://([a-z0-9-]+\\.)?lifestylestores\\.com(:\\d+)?"),
            Pattern.compile("https?://([a-z0-9-]+\\.)?babyshop\\.in(:\\d+)?"),
            Pattern.compile("https?://([a-z0-9-]+\\.)?homecentre\\.in(:\\d+)?"),
            Pattern.compile("https?://([a-z0-9-]+\\.)?landmarkshops\\.in(:\\d+)?"),
            Pattern.compile("https?://localhost(:\\d+)?"),
            Pattern.compile("https?://127\\.0\\.0\\.1(:\\d+)?")
    );

    private static final String WIDGET_PATH = "widget/chatbot-widget.js";

    @GetMapping("/chatbot-widget.js")
    public ResponseEntity<String> serveWidget(HttpServletRequest request) {
        String origin  = request.getHeader("Origin");
        String referer = request.getHeader("Referer");

        String source = origin != null ? origin : referer;

        if (!isAllowed(source)) {
            log.warn("🚫 Widget access denied — Origin: [{}]  Referer: [{}]  IP: [{}]",
                    origin, referer, request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Access denied\",\"message\":\"This script is not available from your domain.\"}");
        }

        try {
            ClassPathResource resource = new ClassPathResource(WIDGET_PATH);
            String content = FileCopyUtils.copyToString(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));

            log.info("✅ Widget served — Origin: [{}]  IP: [{}]", source, request.getRemoteAddr());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/javascript"))
                    .header("Cache-Control", "public, max-age=3600")  // cache 1 hour
                    .body(content);

        } catch (IOException e) {
            log.error("❌ Failed to read widget file: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("// Widget unavailable");
        }
    }

    private boolean isAllowed(String source) {
        if (source == null || source.isBlank()) return false;
        // trim to just origin part if a full Referer URL was passed
        String origin = extractOrigin(source);
        return ALLOWED_ORIGINS.stream().anyMatch(p -> p.matcher(origin).matches());
    }

    private String extractOrigin(String url) {
        // Referer is a full URL like https://lifestylestores.com/page?x=1
        // We only need the scheme+host+port part
        try {
            java.net.URI uri = java.net.URI.create(url);
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port != -1 ? ":" + port : "");
        } catch (Exception e) {
            return url; // fallback: match as-is
        }
    }
}
