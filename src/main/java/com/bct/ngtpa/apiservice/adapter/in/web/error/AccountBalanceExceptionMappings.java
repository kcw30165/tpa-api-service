package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.accountbalance.AccountBalanceRetrievalResponsePolicy;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mapping for {@link AccountBalanceRetrievalException}. Status, {@code ApiStatus} and the error
 * entries all come from the single source of truth, {@link
 * AccountBalanceRetrievalResponsePolicy#map} -- this mapping only re-reads that policy's
 * already-built {@code ResponseEntity} and copies its errors' type/code/targets into {@link
 * ErrorEntry} values, each forced to {@link MessagePolicy#NONE} since the policy's own {@code
 * ApiError} entries already carry {@code message = null} (O-12,
 * docs/design/15-exception-handling/06-open-items.md). The policy class itself is not touched by
 * this mapping and its own tests ({@code AccountBalanceRetrievalResponsePolicyTest}) are
 * unaffected.
 *
 * <p>When {@link AccountBalanceRetrievalException#normalizationFailure()} is present, its
 * value-free diagnostic fields ({@code collection}, {@code rowIndex}, {@code field}, {@code
 * reason}) are returned as {@link ErrorDescriptor#extraLogFields()}, which {@link ErrorEventLogger}
 * merges into the single {@code http.api.error} event it writes.
 */
public final class AccountBalanceExceptionMappings {

  private AccountBalanceExceptionMappings() {}

  public static ExceptionMapping<AccountBalanceRetrievalException> retrievalFailure() {
    return new ExceptionMapping<>() {
      @Override
      public Class<AccountBalanceRetrievalException> exceptionType() {
        return AccountBalanceRetrievalException.class;
      }

      @Override
      public ErrorDescriptor describe(
          AccountBalanceRetrievalException exception, ErrorContext context) {
        var mapped = AccountBalanceRetrievalResponsePolicy.map(exception);
        var body = Objects.requireNonNull(mapped.getBody());
        List<ErrorEntry> entries =
            body.errors().stream()
                .map(
                    error ->
                        new ErrorEntry(
                            error.type(), error.code(), error.targets(), MessagePolicy.NONE))
                .toList();
        boolean logAtError =
            exception.reason() == AccountBalanceRetrievalException.Reason.SYSTEM_FAILURE;
        return new ErrorDescriptor(
            mapped.getStatusCode(),
            body.status(),
            entries,
            "http.api.error",
            logAtError ? LogLevel.ERROR : LogLevel.WARN,
            false,
            normalizationLogFields(exception));
      }

      @Override
      public String diagnosticMessage(AccountBalanceRetrievalException exception) {
        return exception.getMessage();
      }
    };
  }

  private static Map<String, Object> normalizationLogFields(
      AccountBalanceRetrievalException exception) {
    var failure = exception.normalizationFailure();
    if (failure == null) {
      return Map.of();
    }
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put(
        "collection",
        switch (failure.collection()) {
          case DISP_SRC -> "dispSrc";
          case FUND_DTL -> "fundDtl";
          case ACC_BAL -> "accBal";
        });
    fields.put("rowIndex", failure.rowIndex());
    fields.put(
        "field",
        switch (failure.field()) {
          case DISP_SRC -> "disp-src";
          case SUB_DISP_SRC -> "sub-disp-src";
          case FUND_CODE -> "fund-code";
        });
    fields.put("reason", failure.reason().name());
    return fields;
  }
}
