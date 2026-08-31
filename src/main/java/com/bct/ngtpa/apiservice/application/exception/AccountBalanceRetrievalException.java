package com.bct.ngtpa.apiservice.application.exception;

import java.util.Objects;

public final class AccountBalanceRetrievalException extends RuntimeException {
  public enum Reason {
    ACCOUNT_NOT_FOUND,
    ACCOUNT_MISMATCH,
    SYSTEM_FAILURE
  }

  private final Reason reason;

  private AccountBalanceRetrievalException(Reason reason, Throwable cause) {
    super("Account balance retrieval failed: " + Objects.requireNonNull(reason).name(), cause);
    this.reason = reason;
  }

  public static AccountBalanceRetrievalException accountNotFound() {
    return new AccountBalanceRetrievalException(Reason.ACCOUNT_NOT_FOUND, null);
  }

  public static AccountBalanceRetrievalException accountNotFound(Throwable cause) {
    return new AccountBalanceRetrievalException(Reason.ACCOUNT_NOT_FOUND, cause);
  }

  public static AccountBalanceRetrievalException accountMismatch() {
    return new AccountBalanceRetrievalException(Reason.ACCOUNT_MISMATCH, null);
  }

  public static AccountBalanceRetrievalException accountMismatch(Throwable cause) {
    return new AccountBalanceRetrievalException(Reason.ACCOUNT_MISMATCH, cause);
  }

  public static AccountBalanceRetrievalException systemFailure() {
    return new AccountBalanceRetrievalException(Reason.SYSTEM_FAILURE, null);
  }

  public static AccountBalanceRetrievalException systemFailure(Throwable cause) {
    return new AccountBalanceRetrievalException(Reason.SYSTEM_FAILURE, cause);
  }

  public Reason reason() {
    return reason;
  }

  @Override
  public String toString() {
    return "AccountBalanceRetrievalException[reason=" + reason + "]";
  }
}
