package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;
import java.util.Objects;

public record InvestmentChoiceDetailsCommand(
    ActorUserRole actorUserRole,
    String accountEnv,
    String policyNo,
    String certNo,
    LocalDate refDate) {

  public InvestmentChoiceDetailsCommand {
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    Objects.requireNonNull(accountEnv, "accountEnv must not be null");
    Objects.requireNonNull(policyNo, "policyNo must not be null");
    Objects.requireNonNull(certNo, "certNo must not be null");
    Objects.requireNonNull(refDate, "refDate must not be null");
  }
}
