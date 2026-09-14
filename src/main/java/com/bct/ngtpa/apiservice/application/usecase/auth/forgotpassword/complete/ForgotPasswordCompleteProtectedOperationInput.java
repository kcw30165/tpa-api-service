package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import java.util.Objects;

/** Authorized transient input for one acquired Forgot Password COMPLETE execution. */
public record ForgotPasswordCompleteProtectedOperationInput(
    CompleteForgotPasswordCommand command,
    ForgotPasswordAuthFlowContext context,
    AuthFlowSessionSnapshot sessionSnapshot) {
  public ForgotPasswordCompleteProtectedOperationInput {
    Objects.requireNonNull(command, "completion command must not be null");
    Objects.requireNonNull(context, "forgot password context must not be null");
    Objects.requireNonNull(sessionSnapshot, "session snapshot must not be null");
    if (!command.authFlowSessionId().equals(sessionSnapshot.session().authFlowSessionId())) {
      throw new IllegalArgumentException("completion command and session snapshot do not match");
    }
  }

  @Override
  public String toString() {
    return "ForgotPasswordCompleteProtectedOperationInput[command=<redacted>, context=<redacted>, sessionSnapshot=<redacted>]";
  }
}
