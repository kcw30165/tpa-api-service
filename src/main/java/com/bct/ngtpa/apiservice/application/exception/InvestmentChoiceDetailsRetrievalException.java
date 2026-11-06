package com.bct.ngtpa.apiservice.application.exception;

public final class InvestmentChoiceDetailsRetrievalException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private InvestmentChoiceDetailsRetrievalException() {
    super("Investment choice details retrieval failed");
  }

  public static InvestmentChoiceDetailsRetrievalException systemFailure() {
    return new InvestmentChoiceDetailsRetrievalException();
  }
}
