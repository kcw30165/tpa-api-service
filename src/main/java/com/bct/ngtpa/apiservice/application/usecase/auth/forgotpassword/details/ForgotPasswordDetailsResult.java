package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

import java.util.List;

public record ForgotPasswordDetailsResult(
    ForgotPasswordDetailsStatus status, List<String> targets) {
  public ForgotPasswordDetailsResult {
    targets = targets == null ? List.of() : List.copyOf(targets);
  }

  public static ForgotPasswordDetailsResult of(ForgotPasswordDetailsStatus status) {
    return new ForgotPasswordDetailsResult(status, List.of());
  }
}
