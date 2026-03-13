package com.lmg.online.chatbot.ai.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP utility that wraps RestTemplate calls with a single auth-retry:
 * <ol>
 *   <li>Execute the request.</li>
 *   <li>If the server responds 401 / 403, log the failure and re-throw so callers
 *       can surface a meaningful error rather than swallowing auth problems silently.</li>
 * </ol>
 *
 * <p>Inject this bean wherever a resilient, authenticated REST call is needed.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationServiceUtil {

    private final RestTemplate restTemplate;

    /**
     * Execute an HTTP request and return the {@link ResponseEntity}.
     *
     * <p>If the call fails with 401 / 403 the exception is logged and re-thrown.
     * All other exceptions propagate normally.</p>
     *
     * @param appid        app identifier sent as a query / header context value
     * @param url          fully-qualified target URL (already includes query params)
     * @param method       HTTP method ({@code GET}, {@code POST}, …)
     * @param headers      request headers (must not be {@code null})
     * @param body         request body object, or {@code null} for GET/DELETE
     * @param responseType expected response DTO class
     * @param env          environment tag used for logging (e.g. {@code "prod"}, {@code "uat5"})
     * @param <T>          response body type
     * @return {@link ResponseEntity} wrapping the deserialized response body
     */
    public <T> ResponseEntity<T> callWithAuthRetry(
            String appid,
            String url,
            HttpMethod method,
            HttpHeaders headers,
            Object body,
            Class<T> responseType,
            String env) {

        log.info("🌐 HTTP {} → {} [appId={}, env={}]", method, url, appid, env);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<T> response = restTemplate.exchange(url, method, entity, responseType);
            log.info("✅ HTTP {} → {} responded {}", method, url, response.getStatusCode());
            return response;

        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();

            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                log.warn("🔐 Auth failure ({}) for {} {} [appId={}, env={}]. No token-refresh configured — rethrowing.",
                        status.value(), method, url, appid, env);
            } else {
                log.error("❌ HTTP {} error for {} {} [appId={}, env={}]: {}",
                        status.value(), method, url, appid, env, e.getMessage());
            }
            throw e;

        } catch (Exception e) {
            log.error("❌ Unexpected error calling {} {} [appId={}, env={}]: {}",
                    method, url, appid, env, e.getMessage(), e);
            throw e;
        }
    }
}
