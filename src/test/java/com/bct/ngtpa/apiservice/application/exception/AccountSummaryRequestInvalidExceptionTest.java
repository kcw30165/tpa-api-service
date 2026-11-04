package com.bct.ngtpa.apiservice.application.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Mirrors {@code FundPriceRequestInvalidExceptionTest}'s coverage pattern. Deliberately does not
 * assert on any {@code ApplicationException} accessor for the error code (e.g. a {@code code()}
 * method): its exact shape was not independently confirmed during this workstream's
 * investigation, so this test only pins what is confirmed -- the inheritance relationship and
 * message pass-through -- to avoid a guessed assertion.
 */
class AccountSummaryRequestInvalidExceptionTest {

  @Test
  void isAnApplicationException() {
    var exception = new AccountSummaryRequestInvalidException("some message");
    assertThat(exception).isInstanceOf(ApplicationException.class);
  }

  @Test
  void preservesTheSuppliedMessage() {
    var exception = new AccountSummaryRequestInvalidException("currency is not available");
    assertThat(exception.getMessage()).isEqualTo("currency is not available");
  }
}
