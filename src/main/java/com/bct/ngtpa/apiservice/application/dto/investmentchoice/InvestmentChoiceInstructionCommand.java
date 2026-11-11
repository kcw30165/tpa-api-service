package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.List;
import java.util.Objects;

public record InvestmentChoiceInstructionCommand(
    String actorUserId,
    ActorUserRole actorUserRole,
    String accountEnv,
    String policyNo,
    String certNo,
    List<InvestmentChoiceInstructionRow> rows) {

  public InvestmentChoiceInstructionCommand {
    Objects.requireNonNull(actorUserId, "actorUserId must not be null");
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    Objects.requireNonNull(accountEnv, "accountEnv must not be null");
    Objects.requireNonNull(policyNo, "policyNo must not be null");
    Objects.requireNonNull(certNo, "certNo must not be null");
    Objects.requireNonNull(rows, "rows must not be null");
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("rows must not be empty");
    }
    rows = List.copyOf(rows);
  }
}
