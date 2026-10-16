package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first test for the validated, deduplicated hand-off record produced by {@code
 * FundComparisonPricesWebMapper#validate} and consumed by the Milestone 3 prices use case (sub-task
 * 3.3), per docs/design/13-fund-comparison/04-validation-and-error-handling.md section 4.3.
 */
class FundComparisonPricesValidatedRequestTest {

  @Test
  void carriesFundCodesAndTimeRangeVerbatim() {
    var request = new FundComparisonPricesValidatedRequest(List.of("FAEU", "FBAU"), "10D");
    assertThat(request.fundCodes()).containsExactly("FAEU", "FBAU");
    assertThat(request.timeRange()).isEqualTo("10D");
  }

  @Test
  void nullFundCodesThrows() {
    assertThatThrownBy(() -> new FundComparisonPricesValidatedRequest(null, "10D"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fundCodes");
  }

  @Test
  void emptyFundCodesThrows() {
    assertThatThrownBy(() -> new FundComparisonPricesValidatedRequest(List.of(), "10D"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundCodes");
  }

  @Test
  void moreThanFiveFundCodesThrows() {
    var codes = List.of("A", "B", "C", "D", "E", "F");
    assertThatThrownBy(() -> new FundComparisonPricesValidatedRequest(codes, "10D"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("5");
  }

  @Test
  void exactlyFiveFundCodesIsAccepted() {
    var codes = List.of("A", "B", "C", "D", "E");
    var request = new FundComparisonPricesValidatedRequest(codes, "10D");
    assertThat(request.fundCodes()).hasSize(5);
  }

  @Test
  void nullTimeRangeThrows() {
    assertThatThrownBy(() -> new FundComparisonPricesValidatedRequest(List.of("FAEU"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeRange");
  }

  @Test
  void blankTimeRangeThrows() {
    assertThatThrownBy(() -> new FundComparisonPricesValidatedRequest(List.of("FAEU"), " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeRange");
  }

  @Test
  void fundCodesListIsImmutable() {
    var mutable = new ArrayList<String>();
    mutable.add("FAEU");
    var request = new FundComparisonPricesValidatedRequest(mutable, "10D");
    mutable.add("FBAU");
    assertThat(request.fundCodes()).containsExactly("FAEU");
  }
}
