package com.bct.ngtpa.apiservice.application.exception;

public final class InvestmentChoiceSubmissionRejectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvestmentChoiceSubmissionRejectedException() {
    super("Investment choice submission was rejected");
  }
}
