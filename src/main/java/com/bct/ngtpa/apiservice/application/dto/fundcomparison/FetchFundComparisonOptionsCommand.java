package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

/**
 * Inbound command for the TRPGetFundCmpOpt operation. Per
 * docs/design/13-fund-comparison/02-api-contract.md section 2.2 and 06-decisions-and-non-goals.md:
 * policyNo comes from the controller-resolved PortalAccessContext.AccountContext (not
 * PortalAccessContext itself, which is never exposed to the application layer, confirmed script
 * 011), passed in as a primitive.
 */
public record FetchFundComparisonOptionsCommand(
    String policyNo, String accountEnv, ActorUserRole actorUserRole) {

  public FetchFundComparisonOptionsCommand {
    if (policyNo == null || policyNo.isBlank()) {
      throw new IllegalArgumentException("policyNo must not be blank");
    }
    if (accountEnv == null || accountEnv.isBlank()) {
      throw new IllegalArgumentException("accountEnv must not be blank");
    }
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }
}
