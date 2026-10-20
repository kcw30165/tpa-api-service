package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import java.util.List;
import java.util.Objects;

/**
 * Normalized result of the TRPGetFundCmpOpt operation's structural validation. The time-range field
 * is deliberately RAW/unparsed here: grammar validation and normalization
 * (drop-invalid-with-logging, dedup, default-selection) is {@code FundComparisonTimeRangeParser}'s
 * responsibility (built in Milestone 1), not this adapter's or this record's. This record only
 * guarantees the downstream envelope was structurally usable: exactly one data item, a
 * non-null/non-blank time-range string, and a non-empty fund list where every fund has a non-blank
 * code and name.
 */
public record RawFundComparisonOptionsData(
    String rawTimeRange, List<RawFundComparisonOption> funds) {

  public RawFundComparisonOptionsData {
    if (rawTimeRange == null || rawTimeRange.isBlank()) {
      throw new IllegalArgumentException("rawTimeRange must not be blank");
    }
    Objects.requireNonNull(funds, "funds must not be null");
    if (funds.isEmpty()) {
      throw new IllegalArgumentException("funds must not be empty");
    }
    funds = List.copyOf(funds);
  }

  /**
   * A single fund entry within the raw options response: fund code, English name, and Chinese name
   * (chinFundName may be blank or empty -- preserved verbatim, never defaulted, per the adapter's
   * own validation which only requires code and English name to be non-blank).
   */
  public record RawFundComparisonOption(String fundCode, String fundName, String chinFundName) {

    public RawFundComparisonOption {
      if (fundCode == null || fundCode.isBlank()) {
        throw new IllegalArgumentException("fundCode must not be blank");
      }
      if (fundName == null || fundName.isBlank()) {
        throw new IllegalArgumentException("fundName must not be blank");
      }
      chinFundName = chinFundName == null ? "" : chinFundName;
    }
  }
}
