package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first test for the inbound web request DTO for {@code POST /api/v1/fund-comparison/prices},
 * per docs/design/13-fund-comparison/02-api-contract.md section 2.3 (Milestone 3, sub-task 3.2).
 * Business validation (1-5 unique fund codes, time-range syntax) happens in {@code
 * FundComparisonPricesWebMapper#validate}, not in this record's constructor -- this type only
 * guards against a null {@code fundCodes} list so downstream iteration never sees null.
 */
class FundComparisonPricesRequestTest {

  @Test
  void nullFundCodesIsCoalescedToAnEmptyList() {
    var request = new FundComparisonPricesRequest(null, "10D");
    assertThat(request.fundCodes()).isEmpty();
  }

  @Test
  void nonNullFundCodesArePreservedAsAnImmutableCopy() {
    var mutable = new ArrayList<String>();
    mutable.add("FAEU");
    var request = new FundComparisonPricesRequest(mutable, "10D");
    mutable.add("FBAU");
    assertThat(request.fundCodes()).containsExactly("FAEU");
  }

  @Test
  void timeRangeIsPreservedVerbatimIncludingNull() {
    assertThat(new FundComparisonPricesRequest(List.of("FAEU"), "10D").timeRange())
        .isEqualTo("10D");
    assertThat(new FundComparisonPricesRequest(List.of("FAEU"), null).timeRange()).isNull();
  }
}
