package com.bct.ngtpa.apiservice.application.service.auth.forgotpassword;

import java.util.Objects;

public record ForgotPasswordResetAuthorizationValidationResult(
    ForgotPasswordResetAuthorizationValidationStatus status) {

  public ForgotPasswordResetAuthorizationValidationResult {
    Objects.requireNonNull(status, "status must not be null");
  }

  public static ForgotPasswordResetAuthorizationValidationResult of(
      ForgotPasswordResetAuthorizationValidationStatus status) {
    return new ForgotPasswordResetAuthorizationValidationResult(status);
  }
}
