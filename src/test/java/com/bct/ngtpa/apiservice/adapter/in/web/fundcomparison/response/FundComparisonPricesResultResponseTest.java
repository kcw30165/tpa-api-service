package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first test, mirroring {@code FundComparisonOptionsResultResponseTest}-style coverage for the
 * one-field "data" wrapper, per docs/design/13-fund-comparison/07-implementation-plan.md section
 * 7.4 (sub-task 3.4).
 */
class FundComparisonPricesResultResponseTest {

  @Test
  void carriesDataVerbatim() {
    var data = new FundComparisonPricesResponse("06/10/2026", "15/10/2026", List.of());
    var result = new FundComparisonPricesResultResponse(data);
    assertThat(result.data()).isSameAs(data);
  }

  @Test
  void nullDataThrows() {
    assertThatThrownBy(() -> new FundComparisonPricesResultResponse(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("data");
  }
}
