package com.bct.ngtpa.apiservice.application.dto.fundprice;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Inbound command for the TRPGetFundPrice operation.
 *
 * <p>Carries ONE FE-validated date, unmanipulated: Java does not expand it into a from-date/to-date
 * range at this layer. {@code mode} ("DAILY" or "MONTHLY", read from cached {@link
 * FundPriceModeSessionMetadata}) rides alongside so the ADAPTER -- not this command, not the
 * calling use case -- decides how to expand {@code date} into the downstream request's
 * from-date/to-date when building {@code TrpGetFundPriceApimRequest}. This mirrors exactly how
 * {@code ApimFundPriceModeSourceAdapter} alone knows how to shape its own downstream request.
 */
public record FetchFundPriceCommand(
    String policyNo, LocalDate date, String mode, String accountEnv, ActorUserRole actorUserRole) {

  public FetchFundPriceCommand {
    if (policyNo == null || policyNo.isBlank()) {
      throw new IllegalArgumentException("policyNo must not be blank");
    }
    Objects.requireNonNull(date, "date must not be null");
    if (mode == null || mode.isBlank()) {
      throw new IllegalArgumentException("mode must not be blank");
    }
    if (accountEnv == null || accountEnv.isBlank()) {
      throw new IllegalArgumentException("accountEnv must not be blank");
    }
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }
}
