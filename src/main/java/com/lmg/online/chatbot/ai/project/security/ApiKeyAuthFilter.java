package com.lmg.online.chatbot.ai.project.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmg.online.chatbot.ai.project.client.ChatbotClient;
import com.lmg.online.chatbot.ai.project.client.ClientAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates the API key (sent in the configured header) on every PROTECTED request.
 *
 * The key is looked up by its SHA-256 hash in the {@code chatbot_clients} table
 * via {@link ClientAuthService}.  Only active clients with a matching hash are
 * allowed through.
 *
 * Public paths (Swagger, health, admin) are bypassed via {@code shouldNotFilter()}
 * so the filter never runs for them — {@code permitAll()} in SecurityConfig alone
 * is not enough because filters execute before the authorization phase.
 *
 * On success the authenticated principal is set to the client's {@code clientId}
 * (e.g. "lmg-web") so downstream controllers can retrieve it via
 * {@code SecurityContextHolder.getContext().getAuthentication().getName()}.
 */
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ClientAuthService clientAuthService;
    private final ObjectMapper      objectMapper;

    /**
     * Name of the HTTP header that carries the API key, e.g. {@code X-API-Key}.
     * Injected from {@code app.security.api-key-header} via {@link SecurityConfigs}.
     */
    private final String apiKeyHeader;

    // ----------------------------------------------------------------
    // Public paths — filter skipped entirely for these
    // ----------------------------------------------------------------

    /** Exact URIs that require NO API key. */
    private static final Set<String> PUBLIC_EXACT = Set.of(
            "/api/chat/health",
            "/",
            "/index.html"
    );

    /**
     * URI prefixes that require NO API key.
     * /api/admin is excluded here so it is accessible without a client API key;
     * protect it with a separate admin secret or network-level control in production.
     */
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/swagger-ui",
            "/v3/api-docs",
            "/webjars",
            "/api/admin",    // admin REST endpoints use separate protection
            "/admin",        // admin UI static pages
            "/widget",       // chatbot widget JS (domain-checked inside WidgetScriptController)
            "/static",
            "/css",
            "/js",
            "/images"
    );

    /**
     * Tells {@link OncePerRequestFilter} to skip this filter for public paths.
     * Called before {@code doFilterInternal}.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (PUBLIC_EXACT.contains(path)) return true;
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    // ----------------------------------------------------------------
    // Core filter logic
    // ----------------------------------------------------------------

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        // CORS preflight — browsers never send auth headers on OPTIONS requests.
        // Pass through so Spring's CORS filter can respond with the correct headers.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(apiKeyHeader);

        // 1. Key present?
        if (providedKey == null || providedKey.isBlank()) {
            log.warn("API key missing — {} {}", request.getMethod(), request.getRequestURI());
            sendUnauthorized(response,
                    "Missing API key. Provide it in the '" + apiKeyHeader + "' header.");
            return;
        }

        // 2. Key valid and client active?
        Optional<ChatbotClient> clientOpt = clientAuthService.validateApiKey(providedKey);
        if (clientOpt.isEmpty()) {
            log.warn("Invalid/inactive API key — {} {}",
                    request.getMethod(), request.getRequestURI());
            sendUnauthorized(response, "Invalid or inactive API key.");
            return;
        }

        // 3. All good — populate the security context with the client's identity
        ChatbotClient client = clientOpt.get();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                client.getClientId(),   // principal  — e.g. "lmg-web"
                null,                   // credentials — never stored
                List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        log.debug("Authenticated client '{}' — {} {}",
                client.getClientId(), request.getMethod(), request.getRequestURI());

        filterChain.doFilter(request, response);
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private void sendUnauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = Map.of(
                "status",    401,
                "error",     "Unauthorized",
                "message",   message,
                "timestamp", Instant.now().toString()
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
