package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: FundPriceRowResponse does not exist yet. Confirms the bid/offer-vs-unit branching
 * happens via null-field omission (Jackson @JsonInclude(NON_NULL)), not via two distinct response
 * types -- mirrors how the APIM contract itself branches at the field level, one layer up.
 */
class FundPriceRowResponseTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void bidOfferShapeOmitsUnitPriceFieldFromJson() throws Exception {
    var response =
        new FundPriceRowResponse(
            "DUSM",
            "USD Fund",
            "05/05/2026",
            "HKD",
            new BigDecimal("1"),
            new BigDecimal("1"),
            null,
            "5");
    String json = objectMapper.writeValueAsString(response);
    assertThat(json)
        .contains("bidUnitPrice")
        .contains("offerUnitPrice")
        .doesNotContain("unitPrice");
  }

  @Test
  void unitPriceShapeOmitsBidOfferFieldsFromJson() throws Exception {
    var response =
        new FundPriceRowResponse(
            "DUSM", "USD Fund", "05/05/2026", "HKD", null, null, new BigDecimal("1"), "5");
    String json = objectMapper.writeValueAsString(response);
    assertThat(json)
        .contains("unitPrice")
        .doesNotContain("bidUnitPrice")
        .doesNotContain("offerUnitPrice");
  }

  @Test
  void nullRiskLevelOmitsFieldFromJson() throws Exception {
    var response =
        new FundPriceRowResponse(
            "DUSM",
            "USD Fund",
            "05/05/2026",
            "HKD",
            new BigDecimal("1"),
            new BigDecimal("1"),
            null,
            null);
    String json = objectMapper.writeValueAsString(response);
    assertThat(json).doesNotContain("riskLevel");
  }
}
