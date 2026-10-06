package com.bct.ngtpa.apiservice.application.dto.fundprice;

import java.math.BigDecimal;

/**
 * Shared interpretation of downstream fund risk levels ({@code risk-level} on TRPGetFundPrice and
 * {@code max-risk-level} on TRPGetFundPriceMode).
 *
 * <p>{@code null}, blank and {@code "0"} all mean "no risk level assigned": they rank as the
 * highest risk (sorted last) and never count as "a fund carries a risk level" for the {@code
 * maxRiskLevel} echo rule. The raw downstream string is still echoed verbatim on rows.
 */
public final class FundPriceRiskLevels {

  private static final String UNASSIGNED_ZERO = "0";

  private FundPriceRiskLevels() {}

  /** True when {@code riskLevel} is a real, assigned risk level (not null, blank or "0"). */
  public static boolean isAssigned(String riskLevel) {
    return riskLevel != null && !riskLevel.isBlank() && !UNASSIGNED_ZERO.equals(riskLevel.trim());
  }

  /**
   * Numeric sort key, or {@code null} when unassigned or unparseable, so callers can place it last
   * with a nulls-last comparator.
   */
  public static BigDecimal sortKey(String riskLevel) {
    if (!isAssigned(riskLevel)) {
      return null;
    }
    try {
      return new BigDecimal(riskLevel.trim());
    } catch (NumberFormatException exception) {
      return null;
    }
  }
}
