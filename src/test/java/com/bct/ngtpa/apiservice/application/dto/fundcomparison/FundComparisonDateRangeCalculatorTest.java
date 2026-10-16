package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Fail-first test for Fund Comparison's milestone 1, sub-task 1.3 (date-range calculator), per
 * docs/design/13-fund-comparison/01-solution-design.md section 1.4.
 *
 * <p>Every expected value below (including the design pack's own four confirmed examples, plus
 * month-end-clamping and leap-day edge cases) was verified by actually running java.time.LocalDate
 * arithmetic in a standalone harness before being written into these assertions, not
 * hand-calculated.
 */
class FundComparisonDateRangeCalculatorTest {

  private static final LocalDate R = LocalDate.of(2026, 10, 15);

  @Test
  void tenDIsConfirmedByDesignPackExample() {
    var range = FundComparisonDateRangeCalculator.calculate(R, "10D");
    assertThat(range.fromDate()).isEqualTo(LocalDate.of(2026, 10, 6));
    assertThat(range.toDate()).isEqualTo(R);
  }

  @Test
  void oneMIsConfirmedByDesignPackExample() {
    var range = FundComparisonDateRangeCalculator.calculate(R, "1M");
    assertThat(range.fromDate()).isEqualTo(LocalDate.of(2026, 9, 16));
    assertThat(range.toDate()).isEqualTo(R);
  }

  @Test
  void oneYIsConfirmedByDesignPackExample() {
    var range = FundComparisonDateRangeCalculator.calculate(R, "1Y");
    assertThat(range.fromDate()).isEqualTo(LocalDate.of(2025, 10, 16));
    assertThat(range.toDate()).isEqualTo(R);
  }

  @Test
  void ytdIsConfirmedByDesignPackExample() {
    var range = FundComparisonDateRangeCalculator.calculate(R, "YTD");
    assertThat(range.fromDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(range.toDate()).isEqualTo(R);
  }

  @Test
  void everyRangeUsesReferenceDateAsToDate() {
    for (String token : new String[] {"1D", "3M", "6M", "3Y", "YTD"}) {
      assertThat(FundComparisonDateRangeCalculator.calculate(R, token).toDate()).isEqualTo(R);
    }
  }

  @Test
  void monthEndClampingFollowsLocalDateSemantics() {
    // R = 31 March 2026 (non-leap year, so February has 28 days). minusMonths(1) alone
    // clamps 31 March to 28 February; + 1 day = 1 March. Verified via java.time, not
    // hand-calculated.
    var reference = LocalDate.of(2026, 3, 31);
    var range = FundComparisonDateRangeCalculator.calculate(reference, "1M");
    assertThat(range.fromDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(range.toDate()).isEqualTo(reference);
  }

  @Test
  void leapDayReferenceClampsCorrectlyOnYearSubtraction() {
    // R = 29 February 2024 (leap day). minusYears(1) alone clamps to 28 February 2023
    // (2023 is not a leap year); + 1 day = 1 March 2023. Verified via java.time.
    var reference = LocalDate.of(2024, 2, 29);
    var range = FundComparisonDateRangeCalculator.calculate(reference, "1Y");
    assertThat(range.fromDate()).isEqualTo(LocalDate.of(2023, 3, 1));
    assertThat(range.toDate()).isEqualTo(reference);
  }

  @Test
  void ytdWhenReferenceDateIsItselfJanuaryFirst() {
    var reference = LocalDate.of(2026, 1, 1);
    var range = FundComparisonDateRangeCalculator.calculate(reference, "YTD");
    assertThat(range.fromDate()).isEqualTo(reference);
    assertThat(range.toDate()).isEqualTo(reference);
  }

  @Test
  void tenDCrossingAMonthBoundary() {
    var reference = LocalDate.of(2026, 10, 5);
    var range = FundComparisonDateRangeCalculator.calculate(reference, "10D");
    assertThat(range.fromDate()).isEqualTo(LocalDate.of(2026, 9, 26));
    assertThat(range.toDate()).isEqualTo(reference);
  }

  @Test
  void rejectsNullReferenceDate() {
    assertThatThrownBy(() -> FundComparisonDateRangeCalculator.calculate(null, "10D"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullOrMalformedToken() {
    assertThatThrownBy(() -> FundComparisonDateRangeCalculator.calculate(R, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FundComparisonDateRangeCalculator.calculate(R, "bogus"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FundComparisonDateRangeCalculator.calculate(R, "0D"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resultRecordExposesFromDateAndToDate() {
    var range = FundComparisonDateRangeCalculator.calculate(R, "10D");
    assertThat(range).isInstanceOf(FundComparisonDateRange.class);
    assertThat(range.fromDate()).isEqualTo(LocalDate.of(2026, 10, 6));
    assertThat(range.toDate()).isEqualTo(R);
  }
}
