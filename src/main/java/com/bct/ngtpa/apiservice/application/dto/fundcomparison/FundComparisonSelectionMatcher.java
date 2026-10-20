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
 * {@link FundComparisonTimeRangeParser}, so cached and freshly fetched options use the same
 * grammar. On a match the result carries the offered spelling of every fund code (in request order)
 * and of the time-range token. No I/O; the only logging is the parser's own warning for dropped
 * downstream tokens.
 */
public final class FundComparisonSelectionMatcher {

  public static final String TIME_RANGE_NOT_OFFERED = "time-range-not-offered";
  public static final String FUND_CODE_NOT_OFFERED = "fund-code-not-offered";
  public static final String NO_VALID_TIME_RANGE = "no-valid-time-range";

  private FundComparisonSelectionMatcher() {}

  public static Match match(
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
      return Match.rejected(NO_VALID_TIME_RANGE, unlisted);
    }
    String canonicalTimeRange = findOffered(offeredTimeRanges, requestedTimeRange);
    if (canonicalTimeRange == null) {
      return Match.rejected(TIME_RANGE_NOT_OFFERED, unlisted);
    }
    if (!unlisted.isEmpty()) {
      return Match.rejected(FUND_CODE_NOT_OFFERED, unlisted);
    }
    return Match.matched(
        new FundComparisonPricesValidatedRequest(canonicalCodes, canonicalTimeRange));
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

  /**
   * Either a selection in the offered spelling, or a rejection reason, never both. {@code
   * unlistedFundCodes} lists requested codes that were not offered (logged; fund codes are not
   * personal data).
   */
  public record Match(
      FundComparisonPricesValidatedRequest selection,
      String reason,
      List<String> unlistedFundCodes) {

    public Match {
      if ((selection == null) == (reason == null)) {
        throw new IllegalArgumentException("exactly one of selection or reason must be present");
      }
      unlistedFundCodes = unlistedFundCodes == null ? List.of() : List.copyOf(unlistedFundCodes);
    }

    public static Match matched(FundComparisonPricesValidatedRequest selection) {
      Objects.requireNonNull(selection, "selection must not be null");
      return new Match(selection, null, List.of());
    }

    public static Match rejected(String reason, List<String> unlistedFundCodes) {
      return new Match(null, reason, unlistedFundCodes);
    }

    public boolean isMatched() {
      return selection != null;
    }
  }
}
