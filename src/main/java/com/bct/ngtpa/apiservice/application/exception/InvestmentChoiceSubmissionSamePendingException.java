package com.bct.ngtpa.apiservice.application.exception;

public final class InvestmentChoiceSubmissionSamePendingException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvestmentChoiceSubmissionSamePendingException() {
    super("Investment choice submission is blocked by a pending same-source instruction");
  }
}
