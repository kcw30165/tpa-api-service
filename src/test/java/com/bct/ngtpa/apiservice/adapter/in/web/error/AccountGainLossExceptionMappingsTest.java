package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Sub-task 1.7 (M1 closure, Option A), per docs/design/15-exception-handling/05-milestones.md row
 * 1.7's named test. {@link AccountGainLossExceptionMappings} derives status, {@code ApiStatus} and
 * error entries from the single source of truth, {@code AccountGainLossRetrievalResponsePolicy},
 * unchanged, with every entry forced to {@link MessagePolicy#NONE} (confirmed null-message
 * contract, O-12). Sub-task 1.6 already added the {@code X-Request-Id} header and the {@code
 * http.api.error} log line (WARN for business rejection, ERROR for system failure); this test
 * confirms that wiring is unchanged now that it lives in a registered mapping instead of an inline
 * {@code ApiExceptionHandler} method.
 */
class AccountGainLossExceptionMappingsTest {

  private static final ErrorContext CONTEXT =
      new ErrorContext("PATCH", "/api/v1/notifications", "req-1");

  @Test
  void
      fundSwitchingInProgressMapsToUnprocessableBusinessRejectedWithNullMessagePolicyLoggedAtWarn() {
    var mapping = AccountGainLossExceptionMappings.retrievalFailure();
    var exception = AccountGainLossRetrievalException.fundSwitchingInProgress();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = descriptor.errors().getFirst();

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, descriptor.httpStatus());
    assertEquals(ApiStatus.BUSINESS_REJECTED, descriptor.apiStatus());
    assertEquals("BUSINESS", entry.type());
    assertEquals("err.fund.switching.in.progress", entry.code());
    assertEquals(MessagePolicy.NONE, entry.policy());
    assertEquals("http.api.error", descriptor.logEventName());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
    assertEquals(false, descriptor.includeStackTrace());
    assertTrue(descriptor.extraLogFields().isEmpty());
  }

  @Test
  void systemFailureMapsToServiceUnavailableSystemErrorWithNullMessagePolicyLoggedAtError() {
    var mapping = AccountGainLossExceptionMappings.retrievalFailure();
    var exception = AccountGainLossRetrievalException.systemFailure();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = descriptor.errors().getFirst();

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, descriptor.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, descriptor.apiStatus());
    assertEquals("SYSTEM", entry.type());
    assertEquals("err.system.unavailable", entry.code());
    assertEquals(MessagePolicy.NONE, entry.policy());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
  }

  @Test
  void diagnosticMessageIsTheExceptionsOwnMessage() {
    var mapping = AccountGainLossExceptionMappings.retrievalFailure();
    var exception = AccountGainLossRetrievalException.systemFailure();

    assertEquals(exception.getMessage(), mapping.diagnosticMessage(exception));
  }

  @Test
  void exceptionTypeIsAccountGainLossRetrievalException() {
    assertEquals(
        AccountGainLossRetrievalException.class,
        AccountGainLossExceptionMappings.retrievalFailure().exceptionType());
  }
}
