package com.bct.ngtpa.apiservice.application.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** D6 (script 096): the shared TRPGetFundPrice invalid-date exception. */
class FundPriceInvalidDateExceptionTest {

  @Test
  void forInvalidDateBuildsANonBlankMessage() {
    var exception = FundPriceInvalidDateException.forInvalidDate();

    assertThat(exception.getMessage()).isNotBlank();
  }

  @Test
  void isARuntimeExceptionNotAnApplicationException() {
    var exception = FundPriceInvalidDateException.forInvalidDate();

    assertThat(exception)
        .isInstanceOf(RuntimeException.class)
        .isNotInstanceOf(ApplicationException.class);
  }
}
