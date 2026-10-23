package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.internal.referencedate.ReferenceDateController;
import com.bct.ngtpa.apiservice.application.dto.CleanUpAllReferenceDatesResult;
import com.bct.ngtpa.apiservice.application.dto.GetAllReferenceDatesResult;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Sub-task 1.8.3 (M1 closure, A1): the {@code ApiExceptionHandler} constructor call in {@code
 * webClient} changed from (ErrorMessageResolver, LoggingSanitizer, ExceptionMappingRegistry) to
 * (ExceptionMappingRegistry, PublicMessageResolver, ErrorEventLogger, ErrorResponseRenderer). No
 * assertion changed.
 */
class ReferenceDateRefreshControllerTest {

  private static final String REFRESH_PATH = "/api/v1/internal/reference-date/refresh";

  @Test
  void validEndpointPathAcceptsRefreshRequest() {
    RefreshReferenceDateUseCase useCase =
        command -> Mono.just(new RefreshReferenceDateResult("JP", "31/12/2025", true));

    webClient(useCase)
        .post()
        .uri(REFRESH_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("accountEnv", "JP"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.accountEnv")
        .isEqualTo("JP")
        .jsonPath("$.refDate")
        .isEqualTo("31/12/2025")
        .jsonPath("$.configServiceUpdated")
        .doesNotExist()
        .jsonPath("$.redisUpdated")
        .isEqualTo(true);
  }

  @Test
  void validRequestReturnsRefreshResultAndPassesAccountEnvToUseCase() {
    AtomicReference<RefreshReferenceDateCommand> captured = new AtomicReference<>();
    RefreshReferenceDateUseCase useCase =
        command -> {
          captured.set(command);
          return Mono.just(new RefreshReferenceDateResult("JP", "31/12/2025", true));
        };

    webClient(useCase)
        .post()
        .uri(REFRESH_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("accountEnv", "JP"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.accountEnv")
        .isEqualTo("JP")
        .jsonPath("$.refDate")
        .isEqualTo("31/12/2025")
        .jsonPath("$.configServiceUpdated")
        .doesNotExist()
        .jsonPath("$.redisUpdated")
        .isEqualTo(true);

    assertEquals(
        new RefreshReferenceDateCommand(
            "JP", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR),
        captured.get());
  }

  @Test
  void redisPartialFailureStillReturnsSuccess() {
    RefreshReferenceDateUseCase useCase =
        command -> Mono.just(new RefreshReferenceDateResult("JP", "31/12/2025", false));

    webClient(useCase)
        .post()
        .uri(REFRESH_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("accountEnv", "JP"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.accountEnv")
        .isEqualTo("JP")
        .jsonPath("$.refDate")
        .isEqualTo("31/12/2025")
        .jsonPath("$.configServiceUpdated")
        .doesNotExist()
        .jsonPath("$.redisUpdated")
        .isEqualTo(false);
  }

  @Test
  void missingAccountEnvReturnsStandardizedValidationError() {
    webClient(unusedUseCase())
        .post()
        .uri(REFRESH_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("VALIDATION_FAILED")
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.REQUEST_VALIDATION_FAILED)
        .jsonPath("$.errors[0].message")
        .isEqualTo("Invalid request payload.");
  }

  @Test
  void blankAccountEnvReturnsStandardizedValidationError() {
    webClient(unusedUseCase())
        .post()
        .uri(REFRESH_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("accountEnv", "  "))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("VALIDATION_FAILED")
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.REQUEST_VALIDATION_FAILED)
        .jsonPath("$.errors[0].message")
        .isEqualTo("Invalid request payload.");
  }

  private WebTestClient webClient(RefreshReferenceDateUseCase useCase) {
    return WebTestClient.bindToController(
            new ReferenceDateController(
                useCase,
                () -> Mono.just(new GetAllReferenceDatesResult(java.util.List.of())),
                () -> Mono.just(new CleanUpAllReferenceDatesResult(0L))))
        .controllerAdvice(
            new ApiExceptionHandler(
                new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
                new PublicMessageResolver(testErrorMessageResolver()),
                new ErrorEventLogger(testLoggingSanitizer()),
                new ErrorResponseRenderer()))
        .build();
  }

  private static CurrentPortalAccessContextResolver emptyCurrentPortalAccessContextResolver() {
    return new CurrentPortalAccessContextResolver() {
      @Override
      public Mono<PortalAccessContext> current() {
        return Mono.empty();
      }

      @Override
      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.empty();
      }
    };
  }

  private RefreshReferenceDateUseCase unusedUseCase() {
    return command -> Mono.just(new RefreshReferenceDateResult("JP", "31/12/2025", true));
  }

  private static ErrorMessageResolver testErrorMessageResolver() {
    return (errorCode, locale, accountEnv, trustCode, schemeType) ->
        switch (errorCode) {
          case ErrorCodes.REQUEST_VALIDATION_FAILED -> "Invalid request payload.";
          case ErrorCodes.REQUEST_BODY_MALFORMED -> "Malformed request body.";
          default -> "Unexpected error.";
        };
  }

  private static LoggingSanitizer testLoggingSanitizer() {
    return new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties()));
  }
}
