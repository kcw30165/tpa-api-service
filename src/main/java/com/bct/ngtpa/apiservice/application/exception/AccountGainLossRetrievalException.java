package com.bct.ngtpa.apiservice.application.exception;

import java.util.Objects;

public final class AccountGainLossRetrievalException extends RuntimeException {
  public enum Reason { FUND_SWITCHING_IN_PROGRESS, SYSTEM_FAILURE }
  private final Reason reason;

  private AccountGainLossRetrievalException(Reason reason) {
    super(null, null, false, false);
    this.reason = Objects.requireNonNull(reason);
  }

  public static AccountGainLossRetrievalException fundSwitchingInProgress() {
    return new AccountGainLossRetrievalException(Reason.FUND_SWITCHING_IN_PROGRESS);
  }

  public static AccountGainLossRetrievalException systemFailure() {
    return new AccountGainLossRetrievalException(Reason.SYSTEM_FAILURE);
  }

  public Reason reason() { return reason; }

  @Override public String toString() {
    return "AccountGainLossRetrievalException[reason=" + reason + "]";
  }
}
