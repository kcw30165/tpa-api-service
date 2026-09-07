package com.bct.ngtpa.apiservice.application.service.accountbalance;

import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.service.presentation.DynamicDescriptionResolver;

public final class AccountBalanceDynamicDescriptionResolver {
  private final DynamicDescriptionResolver delegate = new DynamicDescriptionResolver();

  public String resolve(String language, String english, String traditionalChinese) {
    try {
      return delegate.resolve(language, english, traditionalChinese);
    } catch (RuntimeException ex) {
      throw AccountBalanceRetrievalException.systemFailure();
    }
  }
}
