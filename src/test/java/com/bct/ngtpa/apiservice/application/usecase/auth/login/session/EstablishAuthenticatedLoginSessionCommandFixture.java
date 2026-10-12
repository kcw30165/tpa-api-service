package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.AuthenticatedLoginHandoffReference;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpConsumptionProof;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerificationOperationReference;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.List;

/**
 * Test-only factory for {@link EstablishAuthenticatedLoginSessionCommand} that supplies
 * fixed values for the components the removed 5-argument constructor used to default: the handoff
 * reference, the verification operation reference, the reservation hash and the login consumption
 * proof. A test that cares about one of those components builds the canonical command itself.
 */
final class EstablishAuthenticatedLoginSessionCommandFixture {

  private EstablishAuthenticatedLoginSessionCommandFixture() {}

  static EstablishAuthenticatedLoginSessionCommand withTestBoundary(
      AuthFlowSessionId authFlowSessionId,
      ActorContext actorContext,
      List<LoginSessionAccount> accounts,
      String csrfTokenHash,
      String correlationId) {
    return new EstablishAuthenticatedLoginSessionCommand(
        authFlowSessionId,
        actorContext,
        accounts,
        csrfTokenHash,
        correlationId,
        new AuthenticatedLoginHandoffReference("00000000000000000000000000000000"),
        new LoginOtpVerificationOperationReference("00000000000000000000000000000000"),
        "legacy-test-boundary",
        new LoginOtpConsumptionProof(
            "legacy-test-boundary",
            new AuthenticatedLoginHandoffReference("00000000000000000000000000000000"),
            new LoginOtpVerificationOperationReference("00000000000000000000000000000000"),
            "legacy-test-boundary",
            java.time.Instant.EPOCH));
  }
}
