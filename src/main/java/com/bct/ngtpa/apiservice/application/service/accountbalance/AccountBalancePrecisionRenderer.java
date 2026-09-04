package com.bct.ngtpa.apiservice.application.service.accountbalance;

import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.service.presentation.ExactPrecisionRenderer;
import java.math.BigDecimal;
import java.math.BigInteger;

public final class AccountBalancePrecisionRenderer {
  private final ExactPrecisionRenderer delegate = new ExactPrecisionRenderer();

  public String render(BigDecimal amount, BigInteger precision) {
    try {
      return delegate.render(amount, precision);
    } catch (RuntimeException ex) {
      throw AccountBalanceRetrievalException.systemFailure();
    }
  }
}
