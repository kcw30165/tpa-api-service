package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Closes the confirmed jacoco gap on FundComparisonDateRange's compact constructor (script 019
 * evidence, line 17/18): the existing FundComparisonDateRangeCalculatorTest only ever constructs
 * this record via the calculator's successful path (fromDate always computed <= toDate), so the
 * fromDate.isAfter(toDate) guard's "true" outcome was never directly exercised.
 */
class FundComparisonDateRangeTest {

  @Test
  void rejectsNullFromDate() {
    assertThatThrownBy(() -> new FundComparisonDateRange(null, LocalDate.of(2026, 10, 15)))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullToDate() {
    assertThatThrownBy(() -> new FundComparisonDateRange(LocalDate.of(2026, 10, 15), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsFromDateAfterToDate() {
    var fromDate = LocalDate.of(2026, 10, 16);
    var toDate = LocalDate.of(2026, 10, 15);
    assertThatThrownBy(() -> new FundComparisonDateRange(fromDate, toDate))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsFromDateEqualToToDate() {
    var sameDate = LocalDate.of(2026, 10, 15);
    var range = new FundComparisonDateRange(sameDate, sameDate);
    assertThat(range.fromDate()).isEqualTo(sameDate);
    assertThat(range.toDate()).isEqualTo(sameDate);
  }

  @Test
  void acceptsFromDateBeforeToDate() {
    var fromDate = LocalDate.of(2026, 10, 6);
    var toDate = LocalDate.of(2026, 10, 15);
    var range = new FundComparisonDateRange(fromDate, toDate);
    assertThat(range.fromDate()).isEqualTo(fromDate);
    assertThat(range.toDate()).isEqualTo(toDate);
  }
}
