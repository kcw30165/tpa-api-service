package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FundComparisonPricePointResponseTest {

  @Test
  void carriesPriceDateAndUnitPriceVerbatim() {
    var point = new FundComparisonPricePointResponse("06/10/2026", new BigDecimal("159.7628"));
    assertThat(point.priceDate()).isEqualTo("06/10/2026");
    assertThat(point.unitPrice()).isEqualByComparingTo("159.7628");
  }
}
