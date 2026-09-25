package com.bct.ngtpa.apiservice.application.dto.fundprice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundPriceExportResultTest {

  private FundPriceRowResult row() {
    return new FundPriceRowResult(
        "DUSM", "n", LocalDate.of(2026, 5, 5), "HKD", BigDecimal.ONE, BigDecimal.ONE, "5");
  }

  @Test
  void carriesRowsFundPriceTypeAndMaxRiskLevel() {
    var result = new FundPriceExportResult(List.of(row()), "BIDOFFER PRICE", "5");
    assertThat(result.rows()).hasSize(1);
    assertThat(result.fundPriceType()).isEqualTo("BIDOFFER PRICE");
    assertThat(result.maxRiskLevel()).isEqualTo("5");
  }
  @Test
  void nullRowsDefaultsToEmptyList() {
    var result = new FundPriceExportResult(null, "UNIT PRICE", null);
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void nullMaxRiskLevelIsAcceptedAndNotDefaulted() {
    var result = new FundPriceExportResult(List.of(row()), "UNIT PRICE", null);
    assertThat(result.maxRiskLevel()).isNull();
  }
}
