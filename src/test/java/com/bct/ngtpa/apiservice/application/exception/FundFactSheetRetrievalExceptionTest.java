package com.bct.ngtpa.apiservice.application.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FundFactSheetRetrievalExceptionTest {

  @Test
  void systemFailureIsAnUncheckedExceptionWithAFixedMessageAndNoCause() {
    var exception = FundFactSheetRetrievalException.systemFailure();

    assertThat(exception).isInstanceOf(RuntimeException.class);
    assertThat(exception.getMessage()).isEqualTo("Fund fact sheet retrieval failed");
    assertThat(exception.getCause()).isNull();
  }

  @Test
  void everyCallReturnsANewInstance() {
    assertThat(FundFactSheetRetrievalException.systemFailure())
        .isNotSameAs(FundFactSheetRetrievalException.systemFailure());
  }
}
