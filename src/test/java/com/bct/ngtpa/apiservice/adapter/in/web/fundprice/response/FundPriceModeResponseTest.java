package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FundPriceModeResponseTest {

  @Test
  void nullAvailableFundPriceDatesDefaultsToEmptyList() {
    var response = new FundPriceModeResponse("DAILY", null, 36, "18/09/2026");
    assertThat(response.availableFundPriceDates()).isEmpty();
  }

  @Test
  void nonNullAvailableFundPriceDatesIsCopiedVerbatim() {
    var dates = List.of("18/09/2026", "18/05/2026");
    var response = new FundPriceModeResponse("MONTHLY", dates, 12, "18/09/2026");
    assertThat(response.availableFundPriceDates()).containsExactlyElementsOf(dates);
    assertThat(response.mode()).isEqualTo("MONTHLY");
    assertThat(response.maximumFundPriceHistoryMonths()).isEqualTo(12);
    assertThat(response.referenceDate()).isEqualTo("18/09/2026");
  }
}
