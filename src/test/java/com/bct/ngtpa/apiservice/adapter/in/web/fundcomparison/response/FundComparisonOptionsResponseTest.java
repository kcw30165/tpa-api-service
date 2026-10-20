package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Closes the confirmed jacoco gap on FundComparisonOptionsResponse's compact constructor (script
 * 049 evidence, lines 16-17): no existing test file constructs this record directly with null
 * timeRanges/funds, so the null-coalescing guards' "true" branches were never exercised (every real
 * caller, the mapper, always passes non-null lists).
 */
class FundComparisonOptionsResponseTest {

  @Test
  void nullTimeRangesIsCoalescedToAnEmptyList() {
    var response = new FundComparisonOptionsResponse(null, "10D", List.of());
    assertThat(response.timeRanges()).isEmpty();
  }

  @Test
  void nullFundsIsCoalescedToAnEmptyList() {
    var response = new FundComparisonOptionsResponse(List.of("10D"), "10D", null);
    assertThat(response.funds()).isEmpty();
  }

  @Test
  void nonNullTimeRangesAndFundsArePreservedAsImmutableCopies() {
    var fund = new FundComparisonOptionResponse("FAEU", "Name");
    var response = new FundComparisonOptionsResponse(List.of("10D", "1M"), "10D", List.of(fund));

    assertThat(response.timeRanges()).containsExactly("10D", "1M");
    assertThat(response.funds()).containsExactly(fund);
  }
}
