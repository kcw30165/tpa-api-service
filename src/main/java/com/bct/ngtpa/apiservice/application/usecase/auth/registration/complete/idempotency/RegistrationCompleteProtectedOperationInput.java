package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationCommand;
import java.util.Objects;

/**
 * Authorized, redacted input for one acquired Registration Complete execution.
 *
 * <p>The future shared coordinator constructs this value only after protected-context validation,
 * expiry refresh, username-state loading, and password validation have succeeded. The value is
 * transient and must never be persisted as replay material.
 */
public record RegistrationCompleteProtectedOperationInput(
    CompleteRegistrationCommand command,
    RegistrationAuthFlowContext registrationContext,
    UsernameCheckState usernameCheckState,
    AuthFlowSession refreshedSession) {

  public RegistrationCompleteProtectedOperationInput {
    Objects.requireNonNull(command, "completion command must not be null");
    Objects.requireNonNull(registrationContext, "registration context must not be null");
    Objects.requireNonNull(usernameCheckState, "username check state must not be null");
    Objects.requireNonNull(refreshedSession, "refreshed session must not be null");

    if (!command.authFlowSessionId().equals(refreshedSession.authFlowSessionId())) {
      throw new IllegalArgumentException("completion command and refreshed session do not match");
    }
  }

  @Override
  public String toString() {
    return "RegistrationCompleteProtectedOperationInput[command=<redacted>, "
        + "registrationContext=<redacted>, usernameCheckState=<redacted>, "
        + "refreshedSession=<redacted>]";
  }
}
