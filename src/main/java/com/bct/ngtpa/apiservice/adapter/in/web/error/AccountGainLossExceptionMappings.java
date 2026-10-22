package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.accountgainloss.AccountGainLossRetrievalResponsePolicy;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sub-task 1.7 (M1 closure, Option A): moves {@code
 * ApiExceptionHandler.handleAccountGainLossRetrievalException}'s mapping out of the handler.
 *
 * <p>Status, {@code ApiStatus} and the error entries all come from the single source of truth,
 * {@link AccountGainLossRetrievalResponsePolicy#map}, unchanged -- this mapping only re-reads that
 * policy's already-built {@code ResponseEntity} and copies its errors' type/code/targets into
 * {@link ErrorEntry} values, each forced to {@link MessagePolicy#NONE} since the policy's own
 * {@code ApiError} entries already carry {@code message = null} (confirmed by sub-task 1.6b's
 * characterization pack, O-12). The policy class itself is not touched by this sub-task and its own
 * tests ({@code AccountGainLossPublicContractTest}) are unaffected.
 *
 * <p>Sub-task 1.6 already added the {@code X-Request-Id} header and the {@code http.api.error} log
 * line for this exception (WARN for business rejection, ERROR for system failure); this sub-task
 * only changes where that wiring lives -- from an inline {@code ApiExceptionHandler} method body to
 * this registered mapping -- with zero behaviour change.
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
