package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first test for Fund Comparison's milestone 1, sub-task 1.2 (time-range parser), per
 * docs/design/13-fund-comparison/07-implementation-plan.md section 7.2 and the exact grammar/rules
 * in 04-validation-and-error-handling.md section 4.2.
 */
class FundComparisonTimeRangeParserTest {

  @Test
  void parsesBothExpectedDownstreamStrings() {
    var first = FundComparisonTimeRangeParser.parse("10D,1M,3M,6M,1Y,YTD,3Y");
    assertThat(first.timeRanges()).containsExactly("10D", "1M", "3M", "6M", "1Y", "YTD", "3Y");

    var second = FundComparisonTimeRangeParser.parse("3M,6M,1Y,YTD,3Y");
    assertThat(second.timeRanges()).containsExactly("3M", "6M", "1Y", "YTD", "3Y");
  }

  @Test
  void trimsWhitespaceAroundTokens() {
    var result = FundComparisonTimeRangeParser.parse(" 10D , 1M ,YTD ");
    assertThat(result.timeRanges()).containsExactly("10D", "1M", "YTD");
  }

  @Test
  void rejectsZeroToken() {
    assertRejectedAsInvalidWhenAloneButKeptWhenMixed("0D");
  }

  @Test
  void rejectsNegativeToken() {
    assertRejectedAsInvalidWhenAloneButKeptWhenMixed("-1D");
  }

  @Test
  void rejectsDecimalToken() {
    assertRejectedAsInvalidWhenAloneButKeptWhenMixed("1.5D");
  }

  @Test
  void rejectsSignedToken() {
    assertRejectedAsInvalidWhenAloneButKeptWhenMixed("+1D");
  }

  @Test
  void rejectsLowercaseUnitToken() {
    assertRejectedAsInvalidWhenAloneButKeptWhenMixed("10d");
  }

  @Test
  void rejectsUnknownUnitToken() {
    assertRejectedAsInvalidWhenAloneButKeptWhenMixed("10W");
  }

  @Test
  void rejectsCombinedToken() {
    assertRejectedAsInvalidWhenAloneButKeptWhenMixed("10D1M");
  }

  private void assertRejectedAsInvalidWhenAloneButKeptWhenMixed(String invalidToken) {
    // Alone: no valid token remains -> fails closed.
    assertThatThrownBy(() -> FundComparisonTimeRangeParser.parse(invalidToken))
        .isInstanceOf(FundComparisonRetrievalException.class);

    // Mixed with a valid token: invalid token is dropped, valid one survives.
    var result = FundComparisonTimeRangeParser.parse(invalidToken + ",10D");
    assertThat(result.timeRanges()).containsExactly("10D");
  }

  @Test
  void removesDuplicatesRetainingFirstOccurrence() {
    var result = FundComparisonTimeRangeParser.parse("10D,1M,10D,YTD,1M");
    assertThat(result.timeRanges()).containsExactly("10D", "1M", "YTD");
  }

  @Test
  void failsWithRetrievalExceptionWhenNothingIsValid() {
    assertThatThrownBy(() -> FundComparisonTimeRangeParser.parse("bogus,also-bogus"))
        .isInstanceOf(FundComparisonRetrievalException.class);
  }

  @Test
  void failsWithRetrievalExceptionWhenInputIsNullOrBlank() {
    assertThatThrownBy(() -> FundComparisonTimeRangeParser.parse(null))
        .isInstanceOf(FundComparisonRetrievalException.class);
    assertThatThrownBy(() -> FundComparisonTimeRangeParser.parse("   "))
        .isInstanceOf(FundComparisonRetrievalException.class);
  }

  @Test
  void selectsDefaultUsingUnitPriorityDThenMThenYThenYtdAndSmallestIntegerWithinAUnit() {
    // D < M < Y < YTD, regardless of array order.
    assertThat(FundComparisonTimeRangeParser.parse("YTD,3Y,6M,10D").defaultTimeRange())
        .isEqualTo("10D");
    assertThat(FundComparisonTimeRangeParser.parse("YTD,3Y,6M").defaultTimeRange()).isEqualTo("6M");
    assertThat(FundComparisonTimeRangeParser.parse("YTD,3Y").defaultTimeRange()).isEqualTo("3Y");
    assertThat(FundComparisonTimeRangeParser.parse("YTD").defaultTimeRange()).isEqualTo("YTD");
  }

  @Test
  void selectsSmallestIntegerWithinTheSameUnit() {
    assertThat(FundComparisonTimeRangeParser.parse("10D,6D,1D").defaultTimeRange()).isEqualTo("1D");
    assertThat(FundComparisonTimeRangeParser.parse("6M,1M,3M").defaultTimeRange()).isEqualTo("1M");
    assertThat(FundComparisonTimeRangeParser.parse("3Y,1Y").defaultTimeRange()).isEqualTo("1Y");
  }

  @Test
  void defaultSelectionIsIndependentOfArrayOrder() {
    var orderA = FundComparisonTimeRangeParser.parse("3Y,10D,1Y");
    var orderB = FundComparisonTimeRangeParser.parse("1Y,3Y,10D");
    assertThat(orderA.defaultTimeRange()).isEqualTo(orderB.defaultTimeRange()).isEqualTo("10D");
  }

  @Test
  void resultRecordExposesTimeRangesAndDefaultTimeRange() {
    var result = FundComparisonTimeRangeParser.parse("10D,1M");
    assertThat(result).isInstanceOf(FundComparisonTimeRangeParseResult.class);
    assertThat(result.timeRanges()).isEqualTo(List.of("10D", "1M"));
    assertThat(result.defaultTimeRange()).isEqualTo("10D");
  }
}
