package com.lmg.online.chatbot.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public class RestTemplateLoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        logRequest(request, body);

        ClientHttpResponse response = execution.execute(request, body);

        logResponse(response);

        return response;
    }

    private void logRequest(HttpRequest request, byte[] body) {
        log.info("➡️ REQUEST: {} {}", request.getMethod(), request.getURI());
        log.info("➡️ Headers: {}", request.getHeaders());
        log.info("➡️ Body: {}", new String(body, StandardCharsets.UTF_8));
    }

    private void logResponse(ClientHttpResponse response) throws IOException {
        String responseBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);

        log.info("⬅️ RESPONSE: {} {}", response.getRawStatusCode(), response.getStatusText());
        log.info("⬅️ Headers: {}", response.getHeaders());
        log.info("⬅️ Body: {}", responseBody);
    }
}