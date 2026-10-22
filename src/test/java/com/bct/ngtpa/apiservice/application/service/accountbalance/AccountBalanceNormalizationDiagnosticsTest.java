package com.bct.ngtpa.apiservice.application.service.accountbalance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException.*;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * Sub-task 1.7 (M1 closure, A2) fix: this test pinned the pre-1.7 behaviour of TWO separate
 * loggingSanitizer.toSafeString(...) calls -- a standalone {@code
 * account.balance.normalization.failed} event followed by the {@code http.api.error} event. A2
 * (approved 2026-10-22) merges these into a SINGLE {@code http.api.error} event carrying the {@code
 * collection}/{@code rowIndex}/{@code field}/{@code reason} fields alongside the normal event
 * fields (requestId, httpMethod, path, httpStatus, errorCode, exceptionType). This test was not in
 * sub-task 1.7's targeted test lists and was missed when {@code
 * ApiExceptionHandlerCharacterizationTest}'s equivalent assertion was updated; fixed here to match
 * the same approved behaviour. No other assertion in this file (status, reason,
 * normalizationFailure, private-name redaction) changes.
 */
class AccountBalanceNormalizationDiagnosticsTest {
  @Test
  void identifiesBothDisplayFieldsAndAllThreeReasonsWithoutRawValues() {
    String[] values = {null, "", " \t"};
    ValidationReason[] reasons = ValidationReason.values();
    for (int i = 0; i < values.length; i++) {
      check(values[i], "S", Field.DISP_SRC, reasons[i]);
      check("D", values[i], Field.SUB_DISP_SRC, reasons[i]);
    }
  }

  private static void check(String display, String sub, Field field, ValidationReason reason) {
    var raw =
        new RawAccountBalanceData(
            null,
            null,
            null,
            List.of(
                new RawAccountBalanceDisplaySource("D0", "private-name", null, "S0", null, null, 0),
                new RawAccountBalanceDisplaySource(
                    display, "private-name", null, sub, null, null, 1)),
            List.of(),
            List.of());
    var thrown = catchThrowable(() -> new DefaultAccountBalanceNormalizer().normalize(raw));
    assertThat(thrown).isInstanceOf(AccountBalanceRetrievalException.class);
    var failure = (AccountBalanceRetrievalException) thrown;
    assertThat(failure.reason()).isEqualTo(Reason.SYSTEM_FAILURE);
    assertThat(failure.normalizationFailure())
        .isEqualTo(new NormalizationFailure(Collection.DISP_SRC, 1, field, reason));
    assertThat(failure.getMessage()).doesNotContain("private-name");
    var sanitizer = mock(LoggingSanitizer.class);
    when(sanitizer.toSafeString(any())).thenReturn("safe-event");
    var handler =
        new ApiExceptionHandler(
            mock(ErrorMessageResolver.class),
            sanitizer,
            new ExceptionMappingRegistryConfig().exceptionMappingRegistry());
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/account/summary"));
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, "diagnostic-request");
    assertThat(
            handler
                .handleAccountBalanceRetrievalException(failure, exchange)
                .getStatusCode()
                .value())
        .isEqualTo(503);
    var captor = ArgumentCaptor.forClass(Object.class);
    verify(sanitizer, times(1)).toSafeString(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(
            Map.ofEntries(
                Map.entry("event", "http.api.error"),
                Map.entry("requestId", "diagnostic-request"),
                Map.entry("httpMethod", "GET"),
                Map.entry("path", "/api/v1/account/summary"),
                Map.entry("httpStatus", 503),
                Map.entry("errorCode", "err.system.unavailable"),
                Map.entry("collection", "dispSrc"),
                Map.entry("rowIndex", 1),
                Map.entry("field", field == Field.DISP_SRC ? "disp-src" : "sub-disp-src"),
                Map.entry("reason", reason.name()),
                Map.entry("exceptionType", "AccountBalanceRetrievalException")));
  }

  @Test
  void preservesValidIdentifierWhitespaceAndNoDiagnosticForOrdinaryFailure() {
    var source = new RawAccountBalanceDisplaySource(" D ", null, null, " S ", null, null, 1);
    var normalized =
        new DefaultAccountBalanceNormalizer()
            .normalize(
                new RawAccountBalanceData(null, null, null, List.of(source), List.of(), List.of()));
    assertThat(normalized.displaySources().keySet().iterator().next().displaySource())
        .isEqualTo(" D ");
    assertThat(AccountBalanceRetrievalException.systemFailure().normalizationFailure()).isNull();
  }
}
