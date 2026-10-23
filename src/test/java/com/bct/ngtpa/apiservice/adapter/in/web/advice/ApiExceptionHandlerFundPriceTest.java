package com.bct.ngtpa.apiservice.adapter.in.web.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRequestInvalidException;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * Fail-first tests confirming Gap B's fix (script 116/118/126): FundPriceRequestInvalidException (a
 * new ApplicationException subclass) gets HTTP 400 via the EXISTING generic ApplicationException
 * handler; FundPriceRetrievalException gets a NEW dedicated @ExceptionHandler mapping to HTTP 503
 * (mirroring CacheException's exact precedent) -- fixing the confirmed pre-existing bug where it
 * previously fell through to the generic @ExceptionHandler(Exception.class) and received HTTP 500
 * instead.
 *
 * <p>Sub-task 1.8.3 (M1 closure, A1): the constructor changed from (ErrorMessageResolver,
 * LoggingSanitizer, ExceptionMappingRegistry) to (ExceptionMappingRegistry, PublicMessageResolver,
 * ErrorEventLogger, ErrorResponseRenderer). {@code handleApplicationException(...)} and {@code
 * handleFundPriceRetrievalException(...)} are both called as {@code handle(...)} now, since both
 * routes resolve through the same single method. No assertion changed.
 */
class ApiExceptionHandlerFundPriceTest {

  private final ErrorMessageResolver errorMessageResolver = mock(ErrorMessageResolver.class);
  private final LoggingSanitizer loggingSanitizer = mock(LoggingSanitizer.class);

  private ApiExceptionHandler newHandler() {
    return new ApiExceptionHandler(
        new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
        new PublicMessageResolver(errorMessageResolver),
        new ErrorEventLogger(loggingSanitizer),
        new ErrorResponseRenderer());
  }

  private MockServerWebExchange exchange() {
    return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/fund-price").build());
  }

  @Test
  void fundPriceRequestInvalidExceptionMapsToHttp400() {
    when(loggingSanitizer.sanitizeText(org.mockito.ArgumentMatchers.any())).thenReturn("msg");
    when(loggingSanitizer.toSafeString(org.mockito.ArgumentMatchers.any())).thenReturn("{}");

    var exception = new FundPriceRequestInvalidException("invalid date");
    var response = newHandler().handle(exception, exchange());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
  }

  @Test
  void fundPriceRetrievalExceptionMapsToHttp503NotHttp500() {
    when(loggingSanitizer.sanitizeText(org.mockito.ArgumentMatchers.any())).thenReturn("msg");
    when(loggingSanitizer.toSafeString(org.mockito.ArgumentMatchers.any())).thenReturn("{}");

    var exception = FundPriceRetrievalException.systemFailure();
    var response = newHandler().handle(exception, exchange());

    // Confirms the fix for the pre-existing bug (script 126): this exception previously fell
    // through to the generic Exception handler and received 500 -- it must now receive 503.
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody().status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
  }
}
