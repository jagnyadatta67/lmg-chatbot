package com.lmg.online.chatbot.ai.project.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Servlet-level CORS filter — runs BEFORE Spring Security, Nginx, or any other filter.
 *
 * Handles OPTIONS preflight immediately (returns 204) and adds
 * Access-Control-Allow-Origin to every response whose Origin matches
 * the known Landmark brand domains.
 *
 * This is the single fix for the preflight not reaching Spring Boot
 * when a reverse proxy (Nginx) is in place.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalCorsFilter extends OncePerRequestFilter {

    private static final List<Pattern> ALLOWED_ORIGINS = List.of(
            Pattern.compile("https?://([a-z0-9-]+\\.)?maxfashion\\.in(:\\d+)?"),
            Pattern.compile("https?://([a-z0-9-]+\\.)?lifestylestores\\.com(:\\d+)?"),
            Pattern.compile("https?://([a-z0-9-]+\\.)?babyshop\\.in(:\\d+)?"),
            Pattern.compile("https?://([a-z0-9-]+\\.)?homecentre\\.in(:\\d+)?"),
            Pattern.compile("https?://([a-z0-9-]+\\.)?landmarkshops\\.in(:\\d+)?"),
            Pattern.compile("https?://localhost(:\\d+)?"),
            Pattern.compile("https?://127\\.0\\.0\\.1(:\\d+)?"),
            // Cloudflare tunnel — dev/testing only
            Pattern.compile("https?://[a-z0-9-]+\\.trycloudflare\\.com(:\\d+)?")
    );

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String origin = request.getHeader("Origin");

        // Always strip any pre-existing CORS headers (prevents nginx duplicate headers)
        response.setHeader("Access-Control-Allow-Origin",      null);
        response.setHeader("Access-Control-Allow-Methods",     null);
        response.setHeader("Access-Control-Allow-Headers",     null);
        response.setHeader("Access-Control-Allow-Credentials", null);
        response.setHeader("Access-Control-Max-Age",           null);
        response.setHeader("Access-Control-Expose-Headers",    null);

        if (origin != null && isAllowedOrigin(origin)) {
            response.setHeader("Access-Control-Allow-Origin",  origin);
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "X-API-Key, Content-Type, Authorization, Accept");
            response.setHeader("Access-Control-Max-Age",       "86400");
            response.setHeader("Vary",                         "Origin");
        }

        // Preflight — respond immediately, no need to reach Spring Security
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isAllowedOrigin(String origin) {
        return ALLOWED_ORIGINS.stream()
                .anyMatch(p -> p.matcher(origin).matches());
    }
}
