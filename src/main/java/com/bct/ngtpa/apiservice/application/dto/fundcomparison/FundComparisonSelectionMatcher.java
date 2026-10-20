package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Matches a prices request against offered options, per
 * docs/design/13-fund-comparison/09-options-session-cache-change.md section 9.5.4.
 *
 * <p>Both sides are trimmed and lower-cased for comparison. The offered time ranges are parsed with
 * {@link FundComparisonTimeRangeParser}, so cached and freshly fetched options use the same grammar.
 * On a match the result carries the offered spelling of every fund code (in request order) and of
 * the time-range token. A pure function: no logging, no I/O.
 */
public final class FundComparisonSelectionMatcher {

  private FundComparisonSelectionMatcher() {}

  public static FundComparisonSelectionMatch match(
      String offeredRawTimeRange,
      List<String> offeredFundCodes,
      List<String> requestedFundCodes,
      String requestedTimeRange) {
    Objects.requireNonNull(offeredFundCodes, "offeredFundCodes must not be null");
    Objects.requireNonNull(requestedFundCodes, "requestedFundCodes must not be null");

    List<String> canonicalCodes = new ArrayList<>();
    List<String> unlisted = new ArrayList<>();
    for (String requested : requestedFundCodes) {
      String offered = findOffered(offeredFundCodes, requested);
      if (offered == null) {
        unlisted.add(requested);
      } else {
        canonicalCodes.add(offered);
      }
    }

    List<String> offeredTimeRanges;
    try {
      offeredTimeRanges = FundComparisonTimeRangeParser.parse(offeredRawTimeRange).timeRanges();
    } catch (FundComparisonRetrievalException exception) {
      return FundComparisonSelectionMatch.rejected(
          FundComparisonSelectionMatch.NO_VALID_TIME_RANGE, unlisted);
    }
    String canonicalTimeRange = findOffered(offeredTimeRanges, requestedTimeRange);
    if (canonicalTimeRange == null) {
      return FundComparisonSelectionMatch.rejected(
          FundComparisonSelectionMatch.TIME_RANGE_NOT_OFFERED, unlisted);
    }
    if (!unlisted.isEmpty()) {
      return FundComparisonSelectionMatch.rejected(
          FundComparisonSelectionMatch.FUND_CODE_NOT_OFFERED, unlisted);
    }
    return FundComparisonSelectionMatch.matched(
        new FundComparisonSelection(canonicalCodes, canonicalTimeRange));
  }

  private static String findOffered(List<String> offered, String requested) {
    String key = normalize(requested);
    if (key.isEmpty()) {
      return null;
    }
    for (String candidate : offered) {
      if (candidate != null && normalize(candidate).equals(key)) {
        return candidate;
      }
    }
    return null;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
