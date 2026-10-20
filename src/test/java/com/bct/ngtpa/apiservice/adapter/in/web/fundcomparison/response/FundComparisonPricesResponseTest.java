package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundComparisonPricesResponseTest {

  @Test
  void carriesFromDateToDateAndSeriesVerbatim() {
    var series = new FundComparisonSeriesResponse("FAEU", List.of());
    var response = new FundComparisonPricesResponse("06/10/2026", "15/10/2026", List.of(series));
    assertThat(response.fromDate()).isEqualTo("06/10/2026");
    assertThat(response.toDate()).isEqualTo("15/10/2026");
    assertThat(response.series()).containsExactly(series);
  }

  @Test
  void nullSeriesIsCoalescedToAnEmptyList() {
    var response = new FundComparisonPricesResponse("06/10/2026", "15/10/2026", null);
    assertThat(response.series()).isEmpty();
  }

  @Test
  void seriesListIsImmutable() {
    var series = new FundComparisonSeriesResponse("FAEU", List.of());
    var mutable = new ArrayList<FundComparisonSeriesResponse>();
    mutable.add(series);
    var response = new FundComparisonPricesResponse("06/10/2026", "15/10/2026", mutable);
    mutable.add(series);
    assertThat(response.series()).hasSize(1);
  }
}
