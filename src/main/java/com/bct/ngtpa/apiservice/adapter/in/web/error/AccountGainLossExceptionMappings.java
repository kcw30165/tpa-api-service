package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.accountgainloss.AccountGainLossRetrievalResponsePolicy;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mapping for {@link AccountGainLossRetrievalException}. Status, {@code ApiStatus} and the error
 * entries all come from the single source of truth, {@link
 * AccountGainLossRetrievalResponsePolicy#map} -- this mapping only re-reads that policy's
 * already-built {@code ResponseEntity} and copies its errors' type/code/targets into {@link
 * ErrorEntry} values, each forced to {@link MessagePolicy#NONE} since the policy's own {@code
 * ApiError} entries already carry {@code message = null} (O-12,
 * docs/design/15-exception-handling/06-open-items.md). The policy class itself is not touched by
 * this mapping and its own tests ({@code AccountGainLossPublicContractTest}) are unaffected.
 */
public final class AccountGainLossExceptionMappings {

  private AccountGainLossExceptionMappings() {}

  public static ExceptionMapping<AccountGainLossRetrievalException> retrievalFailure() {
    return new ExceptionMapping<>() {
      @Override
      public Class<AccountGainLossRetrievalException> exceptionType() {
        return AccountGainLossRetrievalException.class;
      }

      @Override
      public ErrorDescriptor describe(
          AccountGainLossRetrievalException exception, ErrorContext context) {
        var mapped = AccountGainLossRetrievalResponsePolicy.map(exception);
        var body = Objects.requireNonNull(mapped.getBody());
        List<ErrorEntry> entries =
            body.errors().stream()
                .map(
                    error ->
                        new ErrorEntry(
                            error.type(), error.code(), error.targets(), MessagePolicy.NONE))
                .toList();
        boolean logAtError =
            exception.reason() == AccountGainLossRetrievalException.Reason.SYSTEM_FAILURE;
        return new ErrorDescriptor(
            mapped.getStatusCode(),
            body.status(),
            entries,
            "http.api.error",
            logAtError ? LogLevel.ERROR : LogLevel.WARN,
            false,
            Map.of());
      }

      @Override
      public String diagnosticMessage(AccountGainLossRetrievalException exception) {
        return exception.getMessage();
      }
    };
  }
}
