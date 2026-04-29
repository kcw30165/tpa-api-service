package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.exception.ApimException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Pure HTTP transport layer for APIM calls.
 * Handles OAuth2 token attachment and error extraction only.
 * Encryption/decryption is the responsibility of the calling adapter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApimWebClientFacade {

    private final WebClient apimWebClient;
    private final ObjectMapper objectMapper;

    public Mono<String> post(String path, Object requestBody) {
        logRequest(path, requestBody);
        return apimWebClient.post()
                .uri(uriBuilder -> uriBuilder.path(path).build())
                .bodyValue(requestBody)
                .exchangeToMono(this::extractResponseBody);
    }

    private Mono<String> extractResponseBody(ClientResponse response) {
        HttpStatusCode statusCode = response.statusCode();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    if (statusCode.isError()) {
                        log.error("APIM request failed with status={} body={}", statusCode.value(), body);
                        return Mono.error(new ApimException(
                                HttpStatus.BAD_GATEWAY,
                                "APIM request failed with status=%s body=%s".formatted(statusCode.value(), body)));
                    }
                    return Mono.just(body);
                });
    }

    private void logRequest(String path, Object requestBody) {
        try {
            log.info("APIM outbound path={} body={}", path, objectMapper.writeValueAsString(requestBody));
        } catch (Exception ex) {
            log.warn("Failed to serialise APIM request body for logging.", ex);
        }
    }
}
