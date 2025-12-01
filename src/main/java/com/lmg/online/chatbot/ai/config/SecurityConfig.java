package com.lmg.online.chatbot.ai.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(
                "https://*.maxfashion.in",
                "https://*.lifestylestores.com",
                "https://*.babyshop.in",
                "https://*.homecentre.in","http://localhost:8080",  // ✅ allow all localhost ports
                "http://127.0.0.1:8080","https://*.landmarkshops.in/"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }

    // ✅ This filter actually enforces the above CORS config
    @Bean
    public CorsFilter corsFilter() {
        return new CorsFilter(corsConfigurationSource());
    }


    @Bean
    public RestTemplate restTemplate() {

        // -----------------------------
        // Create HTTP Client Pool
        // -----------------------------
        PoolingHttpClientConnectionManager cm =
                new PoolingHttpClientConnectionManager();

        cm.setMaxTotal(200);             // max total connections
        cm.setDefaultMaxPerRoute(50);    // max per host

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        requestFactory.setConnectTimeout(5000);
        requestFactory.setConnectionRequestTimeout(5000);

        RestTemplate restTemplate =
                new RestTemplate(new BufferingClientHttpRequestFactory(requestFactory));

        restTemplate.getInterceptors().add(new RestTemplateLoggingInterceptor());

        return restTemplate;
    }

}