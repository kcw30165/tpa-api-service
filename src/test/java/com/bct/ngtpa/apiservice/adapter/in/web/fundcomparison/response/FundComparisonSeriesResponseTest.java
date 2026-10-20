package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundComparisonSeriesResponseTest {

  @Test
  void carriesFundCodeAndPricesVerbatim() {
    var point = new FundComparisonPricePointResponse("06/10/2026", BigDecimal.ONE);
    var series = new FundComparisonSeriesResponse("FAEU", List.of(point));
    assertThat(series.fundCode()).isEqualTo("FAEU");
    assertThat(series.prices()).containsExactly(point);
  }

  @Test
  void nullPricesIsCoalescedToAnEmptyList() {
    var series = new FundComparisonSeriesResponse("FAEU", null);
    assertThat(series.prices()).isEmpty();
  }

  @Test
  void pricesListIsImmutable() {
    var point = new FundComparisonPricePointResponse("06/10/2026", BigDecimal.ONE);
    var mutable = new ArrayList<FundComparisonPricePointResponse>();
    mutable.add(point);
    var series = new FundComparisonSeriesResponse("FAEU", mutable);
    mutable.add(point);
    assertThat(series.prices()).hasSize(1);
  }
}
