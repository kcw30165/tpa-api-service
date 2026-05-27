package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceDateRefreshControllerTest {

    private static final String REFRESH_PATH = "/api/v1/internal/reference-date/refresh";

    @Test
    void validEndpointPathAcceptsRefreshRequest() {
        RefreshReferenceDateUseCase useCase = command ->
                Mono.just(new RefreshReferenceDateResult("JP", "31/12/2025", true, true));

        webClient(useCase)
                .post()
                .uri(REFRESH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("accountEnv", "JP"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accountEnv").isEqualTo("JP")
                .jsonPath("$.refDate").isEqualTo("31/12/2025")
                .jsonPath("$.configServiceUpdated").isEqualTo(true)
                .jsonPath("$.redisUpdated").isEqualTo(true);
    }

    @Test
    void validRequestReturnsRefreshResultAndPassesAccountEnvToUseCase() {
        AtomicReference<RefreshReferenceDateCommand> captured = new AtomicReference<>();
        RefreshReferenceDateUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(new RefreshReferenceDateResult("JP", "31/12/2025", true, true));
        };

        webClient(useCase)
                .post()
            .uri(REFRESH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("accountEnv", "JP"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accountEnv").isEqualTo("JP")
                .jsonPath("$.refDate").isEqualTo("31/12/2025")
                .jsonPath("$.configServiceUpdated").isEqualTo(true)
                .jsonPath("$.redisUpdated").isEqualTo(true);

        assertEquals(new RefreshReferenceDateCommand("JP"), captured.get());
    }

    @Test
    void redisPartialFailureStillReturnsSuccess() {
        RefreshReferenceDateUseCase useCase = command ->
                Mono.just(new RefreshReferenceDateResult("JP", "31/12/2025", true, false));

        webClient(useCase)
                .post()
            .uri(REFRESH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("accountEnv", "JP"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accountEnv").isEqualTo("JP")
                .jsonPath("$.refDate").isEqualTo("31/12/2025")
                .jsonPath("$.configServiceUpdated").isEqualTo(true)
                .jsonPath("$.redisUpdated").isEqualTo(false);
    }

    @Test
    void missingAccountEnvReturnsStandardizedValidationError() {
        webClient(unusedUseCase())
                .post()
            .uri(REFRESH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo(ErrorCodes.REQUEST_VALIDATION_FAILED)
                .jsonPath("$.message").isEqualTo("Invalid request payload.");
    }

    @Test
    void blankAccountEnvReturnsStandardizedValidationError() {
        webClient(unusedUseCase())
                .post()
            .uri(REFRESH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("accountEnv", "  "))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo(ErrorCodes.REQUEST_VALIDATION_FAILED)
                .jsonPath("$.message").isEqualTo("Invalid request payload.");
    }

    private WebTestClient webClient(RefreshReferenceDateUseCase useCase) {
        return WebTestClient.bindToController(new ReferenceDateRefreshController(useCase))
                .controllerAdvice(new ApiExceptionHandler(testErrorMessageResolver(), testLoggingSanitizer()))
                .build();
    }

    private RefreshReferenceDateUseCase unusedUseCase() {
        return command -> Mono.just(new RefreshReferenceDateResult("JP", "31/12/2025", true, true));
    }

    private static ErrorMessageResolver testErrorMessageResolver() {
        return (errorCode, locale, accountEnv, trustCode, schemeType) -> switch (errorCode) {
            case ErrorCodes.REQUEST_VALIDATION_FAILED -> "Invalid request payload.";
            case ErrorCodes.REQUEST_BODY_MALFORMED -> "Malformed request body.";
            default -> "Unexpected error.";
        };
    }

    private static LoggingSanitizer testLoggingSanitizer() {
        return new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties());
    }
}