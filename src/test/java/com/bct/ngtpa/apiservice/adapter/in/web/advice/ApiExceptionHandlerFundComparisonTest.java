package com.bct.ngtpa.apiservice.adapter.in.web.advice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
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
 * Closes the confirmed jacoco gap on ApiExceptionHandler's handleFundComparisonRetrievalException
 * method (script 019 evidence, lines 352-356, 359, 362 -- exactly the new handler's body as proven
 * by the git-diff-based overlap analysis).
 *
 * <p>The real, already-shipped handler (committed in sub-task 1.1, script 014) builds its ApiError
 * via {@code ApiError.form(errorCode, resolvePublicMessage(errorCode, exchange, ex), List.of(),
 * SOURCE_SERVER)}, mirroring FundPriceRetrievalException's handler shape -- NOT
 * FundFactSheetRetrievalException's handler shape (which this test originally, incorrectly, copied
 * in scripts 020/021/023: {@code ApiError.system(code, null, source)} with the resolver never
 * invoked). This version was corrected after running the real tests and observing the actual,
 * confirmed behavior (type="FORM", message present and resolver-produced, resolver called once per
 * handle() invocation) rather than assuming either sibling's shape a third time.
 */
class ApiExceptionHandlerFundComparisonTest {

  private final AtomicInteger resolverCalls = new AtomicInteger();

  private final ErrorMessageResolver resolver =
      (code, locale, accountEnv, trustCode, schemeType) -> {
        resolverCalls.incrementAndGet();
        return "resolved-" + code;
      };

  private final ApiExceptionHandler handler =
      new ApiExceptionHandler(
          resolver,
          new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties())),
          new ExceptionMappingRegistryConfig().exceptionMappingRegistry());

  private ResponseEntity<MutationResponse<Void>> handle() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/fund-comparison/prices").build());
    return handler.handleFundComparisonRetrievalException(
        FundComparisonRetrievalException.systemFailure(), exchange);
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
  void theErrorCarriesTheCodeAndTheFormTypeAndTheResolvedMessage() {
    MutationResponse<Void> body = handle().getBody();
    var error = body.errors().get(0);
    assertThat(error.code()).isEqualTo("err.fund-comparison.retrieval.unavailable");
    assertThat(error.code()).isEqualTo(ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE);
    assertThat(error.type()).isEqualTo("FORM");
    assertThat(error.message())
        .isEqualTo("resolved-" + ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE);
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
        .isEqualTo("resolved-" + ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE);
    assertThat(error.path("code").asText()).isEqualTo("err.fund-comparison.retrieval.unavailable");
    assertThat(error.path("type").asText()).isEqualTo("FORM");
    assertThat(json.path("status").asText()).isEqualTo("SYSTEM_ERROR");
    assertThat(json.path("success").asBoolean()).isFalse();
  }

  @Test
  void theMessageResolverIsCalledOncePerInvocation() {
    handle();
    handle();
    assertThat(resolverCalls).hasValue(2);
  }

  @Test
  void theErrorCodeConstantHasTheApprovedValue() {
    assertThat(ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE)
        .isEqualTo("err.fund-comparison.retrieval.unavailable");
    assertThat(ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE)
        .isNotEqualTo(ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE);
  }
}
