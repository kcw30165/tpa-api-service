package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.List;
import java.util.Objects;

/**
 * Inbound command for the Milestone 3 prices use case (sub-task 3.3), per
 * docs/design/13-fund-comparison/07-implementation-plan.md section 7.4: "Takes accountEnv/policyNo
 * as primitives (passed in by the controller, not PortalAccessContext)".
 *
 * <p>{@code fundCodes} and {@code timeRange} are expected to already be validated and deduplicated
 * by {@code FundComparisonPricesWebMapper#validate} (sub-task 3.2, which produces {@code
 * FundComparisonPricesValidatedRequest}) -- this record is intentionally self-contained and flat,
 * mirroring {@code FetchFundComparisonOptionsCommand}'s own flat style, rather than nesting that
 * validated-request type, so this use-case's input shape does not couple to the web mapper's
 * intermediate type. It re-enforces the same invariants defensively (non-blank policyNo/accountEnv,
 * 1-5 fund codes, non-blank timeRange), consistent with every other command record in this codebase
 * self-validating regardless of caller.
 *
 * <p>Named with the "UseCaseCommand" suffix, not simply "FetchFundComparisonPricesCommand",
 * specifically to avoid a same-package class-name collision with the existing PORT-level {@code
 * FetchFundComparisonPricesCommand} (sub-task 3.1, same {@code application.dto.fundcomparison}
 * package), which carries genuinely different data: already-computed {@code fromDate}/{@code
 * toDate} and a pre-joined comma-separated fund-list string, both of which this use-case's own
 * command does not yet have (that computation and joining is this use case's own job, performed
 * after receiving this command).
 */
public record FetchFundComparisonPricesUseCaseCommand(
    String policyNo,
    String accountEnv,
    ActorUserRole actorUserRole,
    List<String> fundCodes,
    String timeRange) {

  public FetchFundComparisonPricesUseCaseCommand {
    if (policyNo == null || policyNo.isBlank()) {
      throw new IllegalArgumentException("policyNo must not be blank");
    }
    if (accountEnv == null || accountEnv.isBlank()) {
      throw new IllegalArgumentException("accountEnv must not be blank");
    }
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    Objects.requireNonNull(fundCodes, "fundCodes must not be null");
    if (fundCodes.isEmpty()) {
      throw new IllegalArgumentException("fundCodes must not be empty");
    }
    if (fundCodes.size() > 5) {
      throw new IllegalArgumentException("fundCodes must not contain more than 5 entries");
    }
    if (timeRange == null || timeRange.isBlank()) {
      throw new IllegalArgumentException("timeRange must not be blank");
    }
    fundCodes = List.copyOf(fundCodes);
  }
}
