package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Closes the confirmed jacoco gap on FundComparisonTimeRangeParseResult's compact constructor
 * (script 019 evidence, line 17/18): the existing FundComparisonTimeRangeParserTest only ever
 * constructs this record via the parser's successful path, so the timeRanges.isEmpty() guard's
 * "true" outcome (and the null-rejection guards) were never directly exercised.
 */
class FundComparisonTimeRangeParseResultTest {

  @Test
  void rejectsNullTimeRanges() {
    assertThatThrownBy(() -> new FundComparisonTimeRangeParseResult(null, "10D"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsEmptyTimeRanges() {
    List<String> emptyTimeRanges = List.of();
    assertThatThrownBy(() -> new FundComparisonTimeRangeParseResult(emptyTimeRanges, "10D"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullDefaultTimeRange() {
    List<String> timeRanges = List.of("10D");
    assertThatThrownBy(() -> new FundComparisonTimeRangeParseResult(timeRanges, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void storesAnImmutableCopyOfTimeRanges() {
    var mutableInput = new ArrayList<String>();
    mutableInput.add("10D");
    mutableInput.add("1M");

    var result = new FundComparisonTimeRangeParseResult(mutableInput, "10D");

    // Mutating the caller's original list after construction must not affect the
    // stored value.
    mutableInput.add("3M");
    assertThat(result.timeRanges()).containsExactly("10D", "1M");

    // The stored list itself must be immutable.
    var storedTimeRanges = result.timeRanges();
    assertThatThrownBy(() -> storedTimeRanges.add("YTD"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void acceptsAValidSingleEntryResult() {
    var result = new FundComparisonTimeRangeParseResult(List.of("YTD"), "YTD");
    assertThat(result.timeRanges()).containsExactly("YTD");
    assertThat(result.defaultTimeRange()).isEqualTo("YTD");
  }
}
