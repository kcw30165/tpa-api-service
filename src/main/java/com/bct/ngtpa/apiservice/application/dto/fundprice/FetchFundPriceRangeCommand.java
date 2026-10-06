package com.bct.ngtpa.apiservice.application.dto.fundprice;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Inbound command for the export path's TRPGetFundPrice call. Carries an EXPLICIT fromDate/toDate
 * range with NO mode field at all -- confirmed design (Nick): export has no mode concept; it passes
 * policyNo, fromDate, toDate straight to APIM, bypassing {@link FetchFundPriceCommand}'s
 * single-date + mode-dependent expansion entirely (that remains query-only).
 */
public record FetchFundPriceRangeCommand(
    String policyNo,
    LocalDate fromDate,
    LocalDate toDate,
    String accountEnv,
    ActorUserRole actorUserRole) {

  public FetchFundPriceRangeCommand {
    if (policyNo == null || policyNo.isBlank()) {
      throw new IllegalArgumentException("policyNo must not be blank");
    }
    Objects.requireNonNull(fromDate, "fromDate must not be null");
    Objects.requireNonNull(toDate, "toDate must not be null");
    if (fromDate.isAfter(toDate)) {
      throw new IllegalArgumentException("fromDate must not be after toDate");
    }
    if (accountEnv == null || accountEnv.isBlank()) {
      throw new IllegalArgumentException("accountEnv must not be blank");
    }
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }
}
