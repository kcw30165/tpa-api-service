package com.bct.ngtpa.apiservice.application.dto.fundprice;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Inbound command for the TRPGetFundPriceMode operation.
 *
 * <p>Per docs/design/9-fund-price/fund-price-apim-contract.md Operation 1: {@code policy-no} comes
 * from {@code PortalAccessContext.account().policyNo()} and {@code ref-date} from {@code
 * ReferenceDatePort.resolveReferenceDate(accountEnv)}. FE never supplies these values (see
 * fund-price-master-design.md, Frontend responsibility boundary).
 */
public record FetchFundPriceModeCommand(
    String policyNo, LocalDate referenceDate, String accountEnv, ActorUserRole actorUserRole) {

  public FetchFundPriceModeCommand {
    if (policyNo == null || policyNo.isBlank()) {
      throw new IllegalArgumentException("policyNo must not be blank");
    }
    Objects.requireNonNull(referenceDate, "referenceDate must not be null");
    if (accountEnv == null || accountEnv.isBlank()) {
      throw new IllegalArgumentException("accountEnv must not be blank");
    }
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }
}
