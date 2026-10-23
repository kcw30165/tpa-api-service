package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestHeaderContextWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Sub-task 1.8.3 (M1 closure, A1): the {@code ApiExceptionHandler} constructor call in {@code
 * client} changed from (ErrorMessageResolver, LoggingSanitizer, ExceptionMappingRegistry) to
 * (ExceptionMappingRegistry, PublicMessageResolver, ErrorEventLogger, ErrorResponseRenderer). No
 * assertion changed.
 */
class UpdatePersonalInformationControllerContractTest {

  private static final String REQUEST_ID = "client-request-uuid";
  private static final String ACCEPT_LANGUAGE = "en";

  @Test
  void updatePersonalInformationReturnsGenericBaseErrorWhenAccountRefMissing() {
    client()
        .put()
        .uri("/api/v1/personal-information")
        .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
        .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            new UpdatePersonalInformationRequest(
                "1.0", true, Map.of("emailAddress", "nick@example.test")))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .valueEquals(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.messages")
        .isArray()
        .jsonPath("$.errors")
        .isArray()
        .jsonPath("$.errors[0].code")
        .isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID)
        .jsonPath("$.errorCode")
        .doesNotExist()
        .jsonPath("$.requestId")
        .doesNotExist()
        .consumeWith(result -> assertBodyDoesNotContainRequestId(result.getResponseBody()));
  }

  private static void assertBodyDoesNotContainRequestId(byte[] responseBody) {
    String body = new String(responseBody, StandardCharsets.UTF_8);
    org.junit.jupiter.api.Assertions.assertFalse(
        body.contains("\"requestId\""),
        "X-Request-Id must stay in response headers and must not be serialized in response body");
  }

  private WebTestClient client() {
    UpdatePersonalInformationUseCase useCase = command -> Mono.empty();
    PersonalInformationUpdateWebMapper requestMapper =
        mock(PersonalInformationUpdateWebMapper.class);
    PersonalInformationUpdateResponseMapper responseMapper =
        mock(PersonalInformationUpdateResponseMapper.class);
    PersonalInformationUpdateYamlValidator validator =
        mock(PersonalInformationUpdateYamlValidator.class);
    CurrentPortalAccessContextResolver provider = missingPortalAccessContextProvider();

    return WebTestClient.bindToController(
            controller(useCase, requestMapper, responseMapper, validator, provider))
        .webFilter(requestHeaderContextWebFilter())
        .controllerAdvice(
            new ApiExceptionHandler(
                new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
                new PublicMessageResolver(testErrorMessageResolver()),
                new ErrorEventLogger(testLoggingSanitizer()),
                new ErrorResponseRenderer()))
        .build();
  }

  private RequestHeaderContextWebFilter requestHeaderContextWebFilter() {
    return new RequestHeaderContextWebFilter(
        new RequestLoggingWebFilter(
            new RequestLoggingProperties(), testLoggingSanitizer(), new ObjectMapper()));
  }

  private static ErrorMessageResolver testErrorMessageResolver() {
    return (errorCode, locale, accountEnv, trustCode, schemeType) -> {
      if (ErrorCodes.MEMBER_CONTEXT_INVALID.equals(errorCode)) {
        return "Member context is invalid.";
      }
      return errorCode;
    };
  }

  private static LoggingSanitizer testLoggingSanitizer() {
    MaskingProperties properties = new MaskingProperties();
    properties
        .getLogging()
        .setSensitiveTokens(List.of("policyNo", "certNo", "userId", "apiKey", "token"));
    return new LoggingSanitizer(new ObjectMapper(), new MaskingService(properties));
  }

  private static CurrentPortalAccessContextResolver missingPortalAccessContextProvider() {
    return new CurrentPortalAccessContextResolver() {
      @Override
      public Mono<PortalAccessContext> current() {
        return Mono.error(
            new PortalAccessContextResolutionException(
                ErrorCodes.MEMBER_CONTEXT_INVALID,
                "PortalAccessContext is not available in the current request context."));
      }

      @Override
      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.empty();
      }
    };
  }

  private static PersonalInformationController controller(
      UpdatePersonalInformationUseCase useCase,
      PersonalInformationUpdateWebMapper requestMapper,
      PersonalInformationUpdateResponseMapper responseMapper,
      PersonalInformationUpdateYamlValidator validator,
      CurrentPortalAccessContextResolver resolver) {

    GetPersonalInformationUseCase getUseCase = mock(GetPersonalInformationUseCase.class);
    PersonalInformationWebMapper getRequestMapper = mock(PersonalInformationWebMapper.class);

    return new PersonalInformationController(
        getUseCase, getRequestMapper, resolver, useCase, requestMapper, responseMapper, validator);
  }
}
