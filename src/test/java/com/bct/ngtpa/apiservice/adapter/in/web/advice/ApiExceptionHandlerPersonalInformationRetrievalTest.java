package com.bct.ngtpa.apiservice.adapter.in.web.advice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.exception.PersonalInformationRetrievalException;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/** Decision O-1: a failed member-info lookup on the personal-information PUT is HTTP 503. */
class ApiExceptionHandlerPersonalInformationRetrievalTest {

  private final AtomicInteger resolverCalls = new AtomicInteger();

  private final ErrorMessageResolver resolver =
      (code, locale, accountEnv, trustCode, schemeType) -> {
        resolverCalls.incrementAndGet();
        return "resolved-" + code;
      };

  private final ApiExceptionHandler handler =
      new ApiExceptionHandler(
          resolver,
          new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties())));

  private ResponseEntity<MutationResponse<Void>> handle() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.put("/api/v1/personal-information").build());
    return handler.handlePersonalInformationRetrievalException(
        PersonalInformationRetrievalException.systemFailure(), exchange);
  }

  @Test
  void mapsTheRetrievalFailureToTheServiceUnavailableSystemError() {
    ResponseEntity<MutationResponse<Void>> response = handle();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    MutationResponse<Void> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(body.result()).isNull();
    assertThat(body.messages()).isEmpty();
    assertThat(body.errors()).hasSize(1);
  }

  @Test
  void theErrorCarriesTheCodeAndTheSystemTypeAndNoMessage() {
    MutationResponse<Void> body = handle().getBody();

    var error = body.errors().get(0);
    assertThat(error.code())
        .isEqualTo("err.personal-information.retrieval.unavailable")
        .isEqualTo(ErrorCodes.PERSONAL_INFORMATION_RETRIEVAL_UNAVAILABLE);
    assertThat(error.type()).isEqualTo("SYSTEM");
    assertThat(error.message()).isNull();
    assertThat(error.targets()).isEmpty();
    assertThat(error.severity()).isEqualTo("ERROR");
    assertThat(error.source()).isEqualTo("SERVER");
  }

  @Test
  void theMessageFieldIsOmittedFromTheJson() {
    JsonNode json = new ObjectMapper().valueToTree(handle().getBody());

    JsonNode error = json.path("errors").path(0);
    assertThat(error.has("message")).isFalse();
    assertThat(error.path("code").asText())
        .isEqualTo("err.personal-information.retrieval.unavailable");
    assertThat(error.path("type").asText()).isEqualTo("SYSTEM");
    assertThat(json.path("status").asText()).isEqualTo("SYSTEM_ERROR");
    assertThat(json.path("success").asBoolean()).isFalse();
  }

  @Test
  void theMessageResolverIsNeverCalled() {
    handle();
    handle();

    assertThat(resolverCalls).hasValue(0);
  }

  @Test
  void theErrorCodeConstantHasTheApprovedValue() {
    assertThat(ErrorCodes.PERSONAL_INFORMATION_RETRIEVAL_UNAVAILABLE)
        .isEqualTo("err.personal-information.retrieval.unavailable");
    assertThat(ErrorCodes.PERSONAL_INFORMATION_RETRIEVAL_UNAVAILABLE)
        .isNotEqualTo(ErrorCodes.PERSONAL_INFORMATION_UPDATE_REQUEST_INVALID);
  }
}
