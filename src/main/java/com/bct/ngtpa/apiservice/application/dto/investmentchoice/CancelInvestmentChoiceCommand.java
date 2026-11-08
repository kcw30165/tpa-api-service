package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

public record CancelInvestmentChoiceCommand(
    String actorUserId,
    ActorUserRole actorUserRole,
    String accountEnv,
    String policyNo,
    String certNo,
    String snapshotAccountRef,
    AuthenticatedSessionId authenticatedSessionId,
    String requestId,
    String referenceNo) {

  public CancelInvestmentChoiceCommand {
    Objects.requireNonNull(actorUserId, "actorUserId must not be null");
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    Objects.requireNonNull(accountEnv, "accountEnv must not be null");
    Objects.requireNonNull(policyNo, "policyNo must not be null");
    Objects.requireNonNull(certNo, "certNo must not be null");
    Objects.requireNonNull(snapshotAccountRef, "snapshotAccountRef must not be null");
    Objects.requireNonNull(authenticatedSessionId, "authenticatedSessionId must not be null");
    Objects.requireNonNull(referenceNo, "referenceNo must not be null");
    if (referenceNo.isBlank()) {
      throw new IllegalArgumentException("referenceNo must not be blank");
    }
  }

  @Override
  public String toString() {
    return "CancelInvestmentChoiceCommand[<redacted>]";
  }
}
