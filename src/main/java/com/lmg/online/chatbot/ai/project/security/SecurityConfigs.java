package com.lmg.online.chatbot.ai.project.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmg.online.chatbot.ai.project.client.ClientAuthService;
import com.lmg.online.chatbot.ai.project.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * Spring Security configuration for the Landmark Chatbot API.
 *
 * This is the SINGLE authority for both Spring Security rules AND CORS policy.
 *
 * API key validation:
 *   - {@link ApiKeyAuthFilter} intercepts every protected request.
 *   - The raw key is hashed (SHA-256) and looked up in the {@code chatbot_clients}
 *     table via {@link ClientAuthService}.
 *   - Only active clients with a matching hash are authenticated.
 *
 * Access rules:
 *   PUBLIC    — Swagger UI, OpenAPI docs, health check, admin endpoints
 *   PROTECTED — all other /api/** endpoints require a valid API key
 *
 * Session policy: STATELESS (no cookies, no HTTP sessions).
 * CSRF: disabled (API key replaces form-based auth).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfigs {

    private static final String[] PUBLIC_PATHS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/webjars/**",
            "/api/chat/health",
            "/api/admin/**",        // admin REST endpoints
            "/admin/**",            // admin UI static pages
            "/widget/**"            // chatbot widget JS (domain-checked inside controller)
    };

    private final AppProperties     appProperties;
    private final ClientAuthService clientAuthService;
    private final ObjectMapper      objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless — no HTTP sessions or cookies
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF not needed for a stateless API-key-protected service
            .csrf(AbstractHttpConfigurer::disable)

            // Delegate CORS to our CorsConfigurationSource bean below
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                    // TODO: API key auth temporarily disabled — re-enable when Cloudflare
                    //       Transform Rule is fixed or a non-header auth strategy is in place.
                    //       Restore the lines below and uncomment addFilterBefore() to re-enable.
                    //
                    // .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // .requestMatchers(PUBLIC_PATHS).permitAll()
                    // .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
            )

            // Disable form login and HTTP Basic — API key is the only auth mechanism
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

            // TODO: Re-enable API key filter once auth strategy is confirmed
            // .addFilterBefore(apiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Creates the API-key filter, injecting the DB-backed auth service and
     * the configured header name from {@code app.security.api-key-header}.
     */
    @Bean
    public ApiKeyAuthFilter apiKeyAuthFilter() {
        String headerName = appProperties.getSecurity().getApiKeyHeader();
        return new ApiKeyAuthFilter(clientAuthService, objectMapper, headerName);
    }

    /**
     * CORS policy — single source of truth.
     *
     * Allows requests from all known Landmark front-end domains and
     * localhost for local development.
     *
     * allowCredentials is false because the API uses key-based auth,
     * not cookies — this also keeps the policy compatible with wildcard
     * patterns if ever needed.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                // Brand domains — standard HTTPS + any dev port
                "https://*.maxfashion.in",
                "https://*.maxfashion.in:*",
                "https://*.lifestylestores.com",
                "https://*.lifestylestores.com:*",
                "https://*.babyshop.in",
                "https://*.babyshop.in:*",
                "https://*.homecentre.in",
                "https://*.homecentre.in:*",
                "https://*.landmarkshops.in",
                "https://*.landmarkshops.in:*",
                // Local development
                "http://localhost:*",
                "https://localhost:*",
                "http://127.0.0.1:*",
                "https://127.0.0.1:*",
                // Cloudflare tunnel — dev/testing only
                "https://*.trycloudflare.com"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
