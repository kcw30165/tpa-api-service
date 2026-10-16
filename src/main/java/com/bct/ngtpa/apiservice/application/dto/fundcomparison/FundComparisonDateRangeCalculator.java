package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Computes the inclusive {@code fromDate}/{@code toDate} window for a single, already-validated
 * time-range token, given the reference date {@code R} (resolved by the caller via {@code
 * ReferenceDatePort.resolveReferenceDate(accountEnv)}), per
 * docs/design/13-fund-comparison/01-solution-design.md section 1.4:
 *
 * <ul>
 *   <li>XD: fromDate = R.minusDays(X - 1)
 *   <li>XM: fromDate = R.minusMonths(X).plusDays(1)
 *   <li>XY: fromDate = R.minusYears(X).plusDays(1)
 *   <li>YTD: fromDate = 1 January of R's year
 *   <li>Every range: toDate = R
 * </ul>
 *
 * <p>{@link LocalDate}'s built-in month/year arithmetic (which clamps the day-of-month on overflow,
 * e.g. 31 March minus 1 month becomes 28/29 February) is authoritative for month-end and leap-day
 * cases -- no special-case code is written for these.
 *
 * <p>This class is a pure function with no downstream or Spring dependency; it accepts a single
 * token already validated by the caller against the shared grammar ({@code
 * ^(?:[1-9][0-9]*[DMY]|YTD)$}, confirmed in
 * docs/design/13-fund-comparison/04-validation-and-error-handling.md section 4.2). A malformed
 * token reaching this class is a precondition violation, not a public-facing failure, since every
 * real caller validates syntax before invoking this calculator.
 */
public final class FundComparisonDateRangeCalculator {

  private static final Pattern VALID_TOKEN = Pattern.compile("^(?:[1-9][0-9]*[DMY]|YTD)$");

  private FundComparisonDateRangeCalculator() {}

  public static FundComparisonDateRange calculate(LocalDate referenceDate, String timeRangeToken) {
    Objects.requireNonNull(referenceDate, "referenceDate must not be null");
    if (timeRangeToken == null || !VALID_TOKEN.matcher(timeRangeToken).matches()) {
      throw new IllegalArgumentException(
          "timeRangeToken must match ^(?:[1-9][0-9]*[DMY]|YTD)$, got: " + timeRangeToken);
    }

    LocalDate fromDate;
    if ("YTD".equals(timeRangeToken)) {
      fromDate = LocalDate.of(referenceDate.getYear(), 1, 1);
    } else {
      char unit = timeRangeToken.charAt(timeRangeToken.length() - 1);
      int value = Integer.parseInt(timeRangeToken.substring(0, timeRangeToken.length() - 1));
      fromDate =
          switch (unit) {
            case 'D' -> referenceDate.minusDays(value - 1L);
            case 'M' -> referenceDate.minusMonths(value).plusDays(1);
            case 'Y' -> referenceDate.minusYears(value).plusDays(1);
            default -> throw new IllegalStateException("Unreachable: token already validated");
          };
    }

    return new FundComparisonDateRange(fromDate, referenceDate);
  }
}
