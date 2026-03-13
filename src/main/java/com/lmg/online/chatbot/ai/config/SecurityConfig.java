package com.lmg.online.chatbot.ai.config;

import com.lmg.online.chatbot.ai.config.RestTemplateLoggingInterceptor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP infrastructure configuration.
 *
 * CORS and Spring Security are owned exclusively by
 * {@link com.lmg.online.chatbot.ai.project.security.SecurityConfigs}.
 * This class is intentionally limited to RestTemplate / HTTP-client setup.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public RestTemplate restTemplate() {

        // Pooled HTTP client — prevents connection exhaustion under load
        PoolingHttpClientConnectionManager cm =
                new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(50);

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
