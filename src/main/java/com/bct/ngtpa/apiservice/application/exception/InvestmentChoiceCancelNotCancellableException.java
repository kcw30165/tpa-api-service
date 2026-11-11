package com.bct.ngtpa.apiservice.application.exception;

public final class InvestmentChoiceCancelNotCancellableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvestmentChoiceCancelNotCancellableException() {
    super("Investment choice cancellation was rejected");
  }
}
