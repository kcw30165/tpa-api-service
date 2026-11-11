package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

public record InvestmentChoiceCancelCommand(
    String actorUserId,
    ActorUserRole actorUserRole,
    String accountEnv,
    String policyNo,
    String certNo,
    String referenceNo) {

  public InvestmentChoiceCancelCommand {
    Objects.requireNonNull(actorUserId, "actorUserId must not be null");
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    Objects.requireNonNull(accountEnv, "accountEnv must not be null");
    Objects.requireNonNull(policyNo, "policyNo must not be null");
    Objects.requireNonNull(certNo, "certNo must not be null");
    Objects.requireNonNull(referenceNo, "referenceNo must not be null");
    if (referenceNo.isBlank()) {
      throw new IllegalArgumentException("referenceNo must not be blank");
    }
  }
}
