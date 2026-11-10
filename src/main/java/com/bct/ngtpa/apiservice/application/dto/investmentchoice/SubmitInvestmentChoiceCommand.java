package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;
import java.util.Objects;

public record SubmitInvestmentChoiceCommand(
    String actorUserId,
    ActorUserRole actorUserRole,
    String accountEnv,
    String policyNo,
    String certNo,
    String snapshotAccountRef,
    AuthenticatedSessionId sessionId,
    String requestId,
    InvestmentChoiceInstructionRequest request,
    PayloadDigest authorizationBinding) {

  public SubmitInvestmentChoiceCommand {
    Objects.requireNonNull(actorUserId, "actorUserId must not be null");
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    Objects.requireNonNull(accountEnv, "accountEnv must not be null");
    Objects.requireNonNull(policyNo, "policyNo must not be null");
    Objects.requireNonNull(certNo, "certNo must not be null");
    Objects.requireNonNull(snapshotAccountRef, "snapshotAccountRef must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(authorizationBinding, "authorizationBinding must not be null");
  }

  @Override
  public String toString() {
    return "SubmitInvestmentChoiceCommand[<redacted>]";
  }
}
