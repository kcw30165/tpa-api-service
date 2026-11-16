package com.bct.ngtpa.apiservice.application.dto.vestingscale;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

public record GetVestingScaleCommand(
    String accountEnv,
    String policyNo,
    String certNo,
    String actorUserId,
    ActorUserRole actorUserRole) {

  public GetVestingScaleCommand {
    Objects.requireNonNull(accountEnv, "accountEnv must not be null");
    Objects.requireNonNull(policyNo, "policyNo must not be null");
    Objects.requireNonNull(certNo, "certNo must not be null");
    Objects.requireNonNull(actorUserId, "actorUserId must not be null");
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }

  @Override
  public String toString() {
    return "GetVestingScaleCommand[<redacted>]";
  }
}
