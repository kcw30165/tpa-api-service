package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Inbound command for Fund Comparison's reuse of the TRPGetFundPrice operation, per
 * docs/design/13-fund-comparison/07-implementation-plan.md section 7.4 (corrected sub-task 3.1).
 * Mirrors {@code FetchFundPriceRangeCommand}'s exact guard-clause pattern (confirmed verbatim,
 * script 053 evidence), plus a {@code fundList} guard: this is the ONLY one of the three
 * TRPGetFundPrice commands that carries a real, non-empty, comma-joined fund-code string (joined by
 * the prices use case, confirmed design, sub-task 3.3) -- {@code FetchFundPriceCommand} and {@code
 * FetchFundPriceRangeCommand} always cause {@code ""} to be sent instead, at their own unmodified
 * call sites in {@code ApimFundPriceSourceAdapter}.
 */
public record FetchFundComparisonPricesCommand(
    String policyNo,
    LocalDate fromDate,
    LocalDate toDate,
    String fundList,
    String accountEnv,
    ActorUserRole actorUserRole) {

  public FetchFundComparisonPricesCommand {
    if (policyNo == null || policyNo.isBlank()) {
      throw new IllegalArgumentException("policyNo must not be blank");
    }
    Objects.requireNonNull(fromDate, "fromDate must not be null");
    Objects.requireNonNull(toDate, "toDate must not be null");
    if (fromDate.isAfter(toDate)) {
      throw new IllegalArgumentException("fromDate must not be after toDate");
    }
    if (fundList == null || fundList.isBlank()) {
      throw new IllegalArgumentException("fundList must not be blank");
    }
    if (accountEnv == null || accountEnv.isBlank()) {
      throw new IllegalArgumentException("accountEnv must not be blank");
    }
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }
}
