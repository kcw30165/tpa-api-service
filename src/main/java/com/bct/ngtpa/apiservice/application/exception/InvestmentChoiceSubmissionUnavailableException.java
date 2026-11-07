package com.bct.ngtpa.apiservice.application.exception;

public final class InvestmentChoiceSubmissionUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvestmentChoiceSubmissionUnavailableException() {
    super("Investment choice submission is unavailable");
  }
}
