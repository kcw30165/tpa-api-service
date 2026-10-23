package com.bct.ngtpa.apiservice.adapter.in.web.advice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.exception.FundPriceInvalidDateException;
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

/**
 * D6 (script 096): {@code FundPriceInvalidDateException} maps to HTTP 400 VALIDATION_FAILED {@code
 * err.fund-price.request.invalid}, mirroring {@code ApiExceptionHandlerFundComparisonTest}'s
 * established per-exception test shape (ApiError.form: type FORM, message resolved, severity ERROR,
 * source SERVER) but with a 400/VALIDATION_FAILED mapping instead of 503/SYSTEM_ERROR.
 *
 * <p>Sub-task 1.8.3 (M1 closure, A1): the constructor changed from (ErrorMessageResolver,
 * LoggingSanitizer, ExceptionMappingRegistry) to (ExceptionMappingRegistry, PublicMessageResolver,
 * ErrorEventLogger, ErrorResponseRenderer), and {@code handleFundPriceInvalidDateException(...)} is
 * called as {@code handle(...)}. No assertion changed.
 */
class ApiExceptionHandlerFundPriceInvalidDateTest {

  private final AtomicInteger resolverCalls = new AtomicInteger();
  private final ErrorMessageResolver resolver =
      (code, locale, accountEnv, trustCode, schemeType) -> {
        resolverCalls.incrementAndGet();
        return "resolved-" + code;
      };
  private final ApiExceptionHandler handler =
      new ApiExceptionHandler(
          new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
          new PublicMessageResolver(resolver),
          new ErrorEventLogger(
              new LoggingSanitizer(
                  new ObjectMapper(), new MaskingService(new MaskingProperties()))),
          new ErrorResponseRenderer());

  private ResponseEntity<MutationResponse<Void>> handle() {
    var exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/fund-price").build());
    return handler.handle(FundPriceInvalidDateException.forInvalidDate(), exchange);
  }

  @Test
  void mapsTheInvalidDateFailureToHttp400ValidationFailed() {
    ResponseEntity<MutationResponse<Void>> response = handle();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    MutationResponse<Void> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(body.result()).isNull();
    assertThat(body.messages()).isEmpty();
    assertThat(body.errors()).hasSize(1);
  }

  @Test
  void theErrorCarriesTheFundPriceRequestInvalidCodeAndTheFormTypeAndTheResolvedMessage() {
    MutationResponse<Void> body = handle().getBody();
    var error = body.errors().get(0);

    assertThat(error.code()).isEqualTo("err.fund-price.request.invalid");
    assertThat(error.code()).isEqualTo(ErrorCodes.FUND_PRICE_REQUEST_INVALID);
    assertThat(error.type()).isEqualTo("FORM");
    assertThat(error.message()).isEqualTo("resolved-" + ErrorCodes.FUND_PRICE_REQUEST_INVALID);
    assertThat(error.targets()).isEmpty();
    assertThat(error.severity()).isEqualTo("ERROR");
    assertThat(error.source()).isEqualTo("SERVER");
  }

  @Test
  void theMessageFieldIsPresentInTheJsonWithTheResolvedText() {
    JsonNode json = new ObjectMapper().valueToTree(handle().getBody());
    JsonNode error = json.path("errors").path(0);

    assertThat(error.has("message")).isTrue();
    assertThat(error.path("message").asText())
        .isEqualTo("resolved-" + ErrorCodes.FUND_PRICE_REQUEST_INVALID);
    assertThat(error.path("code").asText()).isEqualTo("err.fund-price.request.invalid");
    assertThat(error.path("type").asText()).isEqualTo("FORM");
    assertThat(json.path("status").asText()).isEqualTo("VALIDATION_FAILED");
    assertThat(json.path("success").asBoolean()).isFalse();
  }

  @Test
  void theMessageResolverIsCalledOncePerInvocation() {
    handle();
    handle();

    assertThat(resolverCalls).hasValue(2);
  }

  @Test
  void theErrorCodeIsDistinctFromTheFundPriceRetrievalUnavailableCode() {
    assertThat(ErrorCodes.FUND_PRICE_REQUEST_INVALID)
        .isEqualTo("err.fund-price.request.invalid")
        .isNotEqualTo(ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE);
  }
}
