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
    verify(sanitizer, times(2)).toSafeString(captor.capture());
    assertThat(captor.getAllValues().getFirst())
        .isEqualTo(
            Map.of(
                "event",
                "account.balance.normalization.failed",
                "requestId",
                "diagnostic-request",
                "collection",
                "dispSrc",
                "rowIndex",
                1,
                "field",
                field == Field.DISP_SRC ? "disp-src" : "sub-disp-src",
                "reason",
                reason.name()));
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
