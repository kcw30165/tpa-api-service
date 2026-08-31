package com.bct.ngtpa.apiservice.application.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountBalanceRetrievalExceptionTest {
  @Test
  void exposesOnlyTypedReasonAndRedactedRepresentation() {
    var ex =
        AccountBalanceRetrievalException.accountNotFound(
            new IllegalStateException("err.tpa.account.invalid raw customer data"));
    assertThat(ex.reason()).isEqualTo(AccountBalanceRetrievalException.Reason.ACCOUNT_NOT_FOUND);
    assertThat(ex.getMessage()).isEqualTo("Account balance retrieval failed: ACCOUNT_NOT_FOUND");
    assertThat(ex.toString()).doesNotContain("err.tpa", "raw customer data");
  }
}
