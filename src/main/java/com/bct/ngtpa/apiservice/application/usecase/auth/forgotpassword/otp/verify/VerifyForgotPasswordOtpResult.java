package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.Objects;

public record VerifyForgotPasswordOtpResult(
    VerifyForgotPasswordOtpStatus status,
    WorkflowNextStep nextStep,
    Integer remainingVerifyAttempts,
    ForgotPasswordOtpVerifyPublicationReceipt publicationReceipt) {
  public VerifyForgotPasswordOtpResult {
    Objects.requireNonNull(status, "status must not be null");
    nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
    if (remainingVerifyAttempts != null && remainingVerifyAttempts < 0) {
      throw new IllegalArgumentException("remainingVerifyAttempts must not be negative");
    }
  }

  public VerifyForgotPasswordOtpResult withPublicationReceipt(
      ForgotPasswordOtpVerifyPublicationReceipt receipt) {
    return new VerifyForgotPasswordOtpResult(
        status, nextStep, remainingVerifyAttempts, Objects.requireNonNull(receipt));
  }

  @Override
  public String toString() {
    return "VerifyForgotPasswordOtpResult[status="
        + status
        + ", nextStep="
        + nextStep
        + ", remainingVerifyAttempts="
        + remainingVerifyAttempts
        + "]";
  }
}
