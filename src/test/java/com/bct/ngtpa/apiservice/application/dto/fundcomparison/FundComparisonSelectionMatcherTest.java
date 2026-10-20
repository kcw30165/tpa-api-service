package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonSelectionMatcher.Match;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fund Comparison M5.4: matching a prices request against offered options (09 section 9.5.4). */
class FundComparisonSelectionMatcherTest {

  private static final String OFFERED_RANGES = "10D,1M,3M,6M,1Y,YTD,3Y";
  private static final List<String> OFFERED_FUNDS = List.of("FAEU", "FBAU", "FCSU");

  @Test
  void matchesWhenTheTimeRangeAndEveryFundCodeAreOffered() {
    var match =
        FundComparisonSelectionMatcher.match(
            OFFERED_RANGES, OFFERED_FUNDS, List.of("FBAU", "FAEU"), "1M");

    assertThat(match.isMatched()).isTrue();
    assertThat(match.selection().fundCodes()).containsExactly("FBAU", "FAEU");
    assertThat(match.selection().timeRange()).isEqualTo("1M");
    assertThat(match.unlistedFundCodes()).isEmpty();
  }

  @Test
  void ignoresCaseAndSurroundingWhitespaceAndReturnsTheOfferedSpelling() {
    var match =
        FundComparisonSelectionMatcher.match(
            OFFERED_RANGES, OFFERED_FUNDS, List.of(" faeu ", "fBaU"), " ytd ");

    assertThat(match.isMatched()).isTrue();
    assertThat(match.selection().fundCodes()).containsExactly("FAEU", "FBAU");
    assertThat(match.selection().timeRange()).isEqualTo("YTD");
  }

  @Test
  void anUnofferedTimeRangeIsRejected() {
    var match =
        FundComparisonSelectionMatcher.match(OFFERED_RANGES, OFFERED_FUNDS, List.of("FAEU"), "10Y");

    assertThat(match.isMatched()).isFalse();
    assertThat(match.reason()).isEqualTo(FundComparisonSelectionMatcher.TIME_RANGE_NOT_OFFERED);
    assertThat(match.selection()).isNull();
  }

  @Test
  void anUnlistedFundCodeIsRejectedAndReported() {
    var match =
        FundComparisonSelectionMatcher.match(
            OFFERED_RANGES, OFFERED_FUNDS, List.of("FAEU", "XXXX"), "1M");

    assertThat(match.isMatched()).isFalse();
    assertThat(match.reason()).isEqualTo(FundComparisonSelectionMatcher.FUND_CODE_NOT_OFFERED);
    assertThat(match.unlistedFundCodes()).containsExactly("XXXX");
  }

  @Test
  void theTimeRangeReasonWinsButUnlistedCodesAreStillReported() {
    var match =
        FundComparisonSelectionMatcher.match(OFFERED_RANGES, OFFERED_FUNDS, List.of("XXXX"), "2D");

    assertThat(match.reason()).isEqualTo(FundComparisonSelectionMatcher.TIME_RANGE_NOT_OFFERED);
    assertThat(match.unlistedFundCodes()).containsExactly("XXXX");
  }

  @Test
  void optionsWithoutAnyValidTimeRangeAreRejected() {
    var match = FundComparisonSelectionMatcher.match("bogus", OFFERED_FUNDS, List.of("FAEU"), "1M");

    assertThat(match.isMatched()).isFalse();
    assertThat(match.reason()).isEqualTo(FundComparisonSelectionMatcher.NO_VALID_TIME_RANGE);
  }

  @Test
  void aMissingRequestedTimeRangeIsNotOffered() {
    var match =
        FundComparisonSelectionMatcher.match(OFFERED_RANGES, OFFERED_FUNDS, List.of("FAEU"), null);

    assertThat(match.reason()).isEqualTo(FundComparisonSelectionMatcher.TIME_RANGE_NOT_OFFERED);
  }

  @Test
  void aBlankRequestedFundCodeIsUnlisted() {
    var match =
        FundComparisonSelectionMatcher.match(OFFERED_RANGES, OFFERED_FUNDS, List.of(" "), "1M");

    assertThat(match.reason()).isEqualTo(FundComparisonSelectionMatcher.FUND_CODE_NOT_OFFERED);
    assertThat(match.unlistedFundCodes()).containsExactly(" ");
  }

  @Test
  void aNullOfferedFundCodeIsSkipped() {
    var offered = Arrays.asList(null, "FAEU");

    var match =
        FundComparisonSelectionMatcher.match(OFFERED_RANGES, offered, List.of("faeu"), "1M");

    assertThat(match.isMatched()).isTrue();
    assertThat(match.selection().fundCodes()).containsExactly("FAEU");
  }

  @Test
  void requiresTheFundCodeLists() {
    var requested = List.of("FAEU");

    assertThatThrownBy(
            () -> FundComparisonSelectionMatcher.match(OFFERED_RANGES, null, requested, "1M"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> FundComparisonSelectionMatcher.match(OFFERED_RANGES, OFFERED_FUNDS, null, "1M"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void theMatchRecordHoldsExactlyOneOfSelectionOrReason() {
    var selection = new FundComparisonPricesValidatedRequest(List.of("FAEU"), "1M");
    var unlisted = List.<String>of();

    assertThatThrownBy(() -> new Match(null, null, unlisted))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Match(selection, "reason", unlisted))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new Match(null, "reason", null).unlistedFundCodes()).isEmpty();
    assertThatThrownBy(() -> Match.matched(null)).isInstanceOf(NullPointerException.class);
  }
}
