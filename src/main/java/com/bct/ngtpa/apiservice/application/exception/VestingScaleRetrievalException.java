package com.bct.ngtpa.apiservice.application.exception;

public final class VestingScaleRetrievalException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private VestingScaleRetrievalException() {
    super("Vesting scale retrieval failed");
  }

  public static VestingScaleRetrievalException systemFailure() {
    return new VestingScaleRetrievalException();
  }
}
