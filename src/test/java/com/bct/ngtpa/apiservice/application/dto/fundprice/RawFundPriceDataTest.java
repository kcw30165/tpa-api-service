package com.bct.ngtpa.apiservice.application.dto.fundprice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RawFundPriceDataTest {

  private RawFundPriceRow row() {
    return new RawFundPriceRow(
        "DUSM",
        "Fidelity USD Money Market Fund",
        "Fidelity USD Money Market Fund",
        LocalDate.of(2026, 5, 5),
        "HKD",
        BigDecimal.ONE,
        BigDecimal.ONE,
        "5");
  }

  @Test
  void carriesRowsVerbatim() {
    var data = new RawFundPriceData(List.of(row()));
    assertThat(data.rows()).hasSize(1);
    assertThat(data.rows().get(0).fundCode()).isEqualTo("DUSM");
  }

  @Test
  void nullRowsDefaultsToEmptyList() {
    var data = new RawFundPriceData(null);
    assertThat(data.rows()).isEmpty();
  }

  @Test
  void rowsListIsImmutable() {
    var mutable = new java.util.ArrayList<RawFundPriceRow>();
    mutable.add(row());
    var data = new RawFundPriceData(mutable);
    mutable.add(row());
    assertThat(data.rows()).hasSize(1);
  }
}
