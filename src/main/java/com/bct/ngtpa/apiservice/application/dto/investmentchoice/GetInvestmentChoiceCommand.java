package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

public record GetInvestmentChoiceCommand(
    ActorUserRole actorUserRole,
    String accountEnv,
    String policyNo,
    String certNo,
    String accountRef,
    AuthenticatedSessionId sessionId,
    String requestId) {

  public GetInvestmentChoiceCommand {
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    Objects.requireNonNull(accountEnv, "accountEnv must not be null");
    Objects.requireNonNull(policyNo, "policyNo must not be null");
    Objects.requireNonNull(certNo, "certNo must not be null");
    Objects.requireNonNull(accountRef, "accountRef must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
  }

  @Override
  public String toString() {
    return "GetInvestmentChoiceCommand[<redacted>]";
  }
}
