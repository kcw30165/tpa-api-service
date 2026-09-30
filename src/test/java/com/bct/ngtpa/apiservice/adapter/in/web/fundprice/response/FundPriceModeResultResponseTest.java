package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class FundPriceModeResultResponseTest {

  private FundPriceModeResponse validData() {
    return new FundPriceModeResponse("DAILY", List.of("18/09/2026"), 36, "18/09/2026");
  }

  @Test
  void nullDataThrows() {
    assertThatThrownBy(() -> new FundPriceModeResultResponse(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nonNullDataIsExposedVerbatim() {
    var data = validData();
    var result = new FundPriceModeResultResponse(data);
    assertThat(result.data()).isEqualTo(data);
  }
}
