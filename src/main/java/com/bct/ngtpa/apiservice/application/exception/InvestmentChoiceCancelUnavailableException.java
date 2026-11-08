package com.bct.ngtpa.apiservice.application.exception;

public final class InvestmentChoiceCancelUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvestmentChoiceCancelUnavailableException() {
    super("Investment choice cancellation is unavailable");
  }
}
