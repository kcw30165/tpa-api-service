package com.bct.ngtpa.apiservice.application.exception;

public final class InvestmentChoiceSubmissionUnknownOutcomeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvestmentChoiceSubmissionUnknownOutcomeException() {
    super("Investment choice submission outcome is unknown");
  }
}
