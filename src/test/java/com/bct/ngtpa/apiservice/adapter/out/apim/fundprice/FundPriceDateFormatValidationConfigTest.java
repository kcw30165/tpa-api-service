package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The real fundPriceFullDateFormatter bean path: validation at construction, strict parsing. */
class FundPriceDateFormatValidationConfigTest {

  @Test
  void beanFormatterParsesRealApimDate() {
    FundPriceDateFormatProperties properties = new FundPriceDateFormatProperties();
    FundPriceDateFormatValidationConfig config = new FundPriceDateFormatValidationConfig(properties);

    assertThat(LocalDate.parse("10/07/2026", config.fundPriceFullDateFormatter(properties)))
        .isEqualTo(LocalDate.of(2026, 7, 10));
  }

  @Test
  void yearOfEraConfigurationFailsAtConstruction() {
    FundPriceDateFormatProperties properties = new FundPriceDateFormatProperties();
    properties.setFullDate("dd/MM/yyyy");

    assertThatThrownBy(() -> new FundPriceDateFormatValidationConfig(properties))
        .isInstanceOf(IllegalStateException.class);
  }
}
