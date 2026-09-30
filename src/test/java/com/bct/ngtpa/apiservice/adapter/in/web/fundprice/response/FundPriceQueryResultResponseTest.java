package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class FundPriceQueryResultResponseTest {

  @Test
  void nullDataThrows() {
    assertThatThrownBy(() -> new FundPriceQueryResultResponse(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nonNullDataIsExposedVerbatim() {
    var data = new FundPriceQueryResponse(List.of(), "5");
    assertThat(new FundPriceQueryResultResponse(data).data()).isEqualTo(data);
  }
}
