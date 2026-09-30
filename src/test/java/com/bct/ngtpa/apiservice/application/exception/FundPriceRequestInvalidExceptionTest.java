package com.bct.ngtpa.apiservice.application.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: FundPriceRequestInvalidException does not exist yet. Confirms it extends
 * ApplicationException (mirroring InvalidContributionRequestException's exact one-line shape) --
 * the existing generic @ExceptionHandler(ApplicationException.class) in ApiExceptionHandler
 * already maps ANY subclass to HTTP 400 automatically; no new handler method is needed.
 */
class FundPriceRequestInvalidExceptionTest {

  @Test
  void isAnApplicationException() {
    var exception = new FundPriceRequestInvalidException("date is not a valid available fund price date");
    assertThat(exception).isInstanceOf(ApplicationException.class);
  }

  @Test
  void carriesTheStableFundPriceRequestInvalidErrorCode() {
    var exception = new FundPriceRequestInvalidException("some message");
    assertThat(exception.getErrorCode()).isEqualTo(ErrorCodes.FUND_PRICE_REQUEST_INVALID);
  }

  @Test
  void carriesTheDiagnosticMessage() {
    var exception = new FundPriceRequestInvalidException("date must be in dd/MM/yyyy format");
    assertThat(exception.getMessage()).isEqualTo("date must be in dd/MM/yyyy format");
  }
}
