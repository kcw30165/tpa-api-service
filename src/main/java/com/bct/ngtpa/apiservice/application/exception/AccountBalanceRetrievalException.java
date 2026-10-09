package com.bct.ngtpa.apiservice.application.exception;

import java.io.Serializable;
import java.util.Objects;

public final class AccountBalanceRetrievalException extends RuntimeException {
  public enum Reason {
    ACCOUNT_NOT_FOUND,
    ACCOUNT_MISMATCH,
    SYSTEM_FAILURE
  }

  public enum Collection {
    DISP_SRC,
    FUND_DTL,
    ACC_BAL
  }

  public enum Field {
    DISP_SRC,
    SUB_DISP_SRC,
    FUND_CODE
  }

  public enum ValidationReason {
    NULL,
    EMPTY,
    WHITESPACE_ONLY
  }

  public record NormalizationFailure(
      Collection collection, int rowIndex, Field field, ValidationReason reason)
      implements Serializable {
    public NormalizationFailure {
      Objects.requireNonNull(collection);
      Objects.requireNonNull(field);
      Objects.requireNonNull(reason);
      if (rowIndex < 0) throw new IllegalArgumentException("rowIndex must be nonnegative");
    }
  }

  private final Reason reason;
  private final NormalizationFailure normalizationFailure;

  private AccountBalanceRetrievalException(Reason reason, Throwable cause) {
    this(reason, cause, null);
  }

  private AccountBalanceRetrievalException(
      Reason reason, Throwable cause, NormalizationFailure normalizationFailure) {
    super("Account balance retrieval failed: " + Objects.requireNonNull(reason).name(), cause);
    this.reason = reason;
    this.normalizationFailure = normalizationFailure;
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

  public static AccountBalanceRetrievalException normalizationFailure(
      NormalizationFailure failure) {
    return new AccountBalanceRetrievalException(
        Reason.SYSTEM_FAILURE, null, Objects.requireNonNull(failure));
  }

  public NormalizationFailure normalizationFailure() {
    return normalizationFailure;
  }

  public Reason reason() {
    return reason;
  }

  @Override
  public String toString() {
    return "AccountBalanceRetrievalException[reason=" + reason + "]";
  }
}
