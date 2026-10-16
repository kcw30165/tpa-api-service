package com.bct.ngtpa.apiservice.application.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.junit.jupiter.api.Test;

/**
 * Fail-first test for Fund Comparison's milestone 1, sub-task 1.1 (error codes), per
 * docs/design/13-fund-comparison/07-implementation-plan.md section 7.2. Mirrors the existing
 * FundPriceRequestInvalidException / FundPriceRetrievalException coverage pattern for the sibling
 * Fund Price exceptions (confirmed shapes, script 014's investigation).
 */
class FundComparisonErrorCodesAndExceptionsTest {

  @Test
  void requestInvalidErrorCodeMatchesConfirmedContract() {
    assertThat(ErrorCodes.FUND_COMPARISON_REQUEST_INVALID)
        .isEqualTo("err.fund-comparison.request.invalid");
  }

  @Test
  void retrievalUnavailableErrorCodeMatchesConfirmedContract() {
    assertThat(ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE)
        .isEqualTo("err.fund-comparison.retrieval.unavailable");
  }

  @Test
  void requestInvalidExceptionCarriesTheRequestInvalidErrorCode() {
    var exception = new FundComparisonRequestInvalidException("fundCodes must not be blank");

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCodes.FUND_COMPARISON_REQUEST_INVALID);
    assertThat(exception.getMessage()).isEqualTo("fundCodes must not be blank");
  }

  @Test
  void retrievalExceptionSystemFailureCarriesTheRetrievalUnavailableErrorCode() {
    var exception = FundComparisonRetrievalException.systemFailure();

    assertThat(exception.getErrorCode())
        .isEqualTo(ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE);
  }
}
