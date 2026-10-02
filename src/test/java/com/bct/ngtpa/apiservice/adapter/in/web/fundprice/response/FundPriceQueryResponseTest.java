package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundPriceQueryResponseTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private FundPriceRowResponse row() {
    return new FundPriceRowResponse(
        "DUSM",
        "USD Fund",
        "05/05/2026",
        "HKD",
        new BigDecimal("1"),
        new BigDecimal("1"),
        null,
        "5");
  }

  @Test
  void carriesRowsAndMaxRiskLevel() {
    var response = new FundPriceQueryResponse(List.of(row()), "5");
    assertThat(response.rows()).hasSize(1);
    assertThat(response.maxRiskLevel()).isEqualTo("5");
  }

  @Test
  void nullMaxRiskLevelOmitsFieldFromJson() throws Exception {
    var response = new FundPriceQueryResponse(List.of(row()), null);
    String json = objectMapper.writeValueAsString(response);
    assertThat(json).doesNotContain("maxRiskLevel");
  }

  @Test
  void nullRowsDefaultsToEmptyList() {
    var response = new FundPriceQueryResponse(null, "5");
    assertThat(response.rows()).isEmpty();
  }
}
