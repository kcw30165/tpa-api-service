package com.bct.ngtpa.apiservice.application.dto.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Closes the coverage gap identified by script 030: the {@code availableFundPriceDates == null ?
 * List.of() : List.copyOf(...)} ternary was only ever exercised with a non-null list. Targets 95%
 * instruction / 95% branch (DTO tier).
 */
class RawFundPriceModeDataTest {

  @Test
  void nullAvailableFundPriceDatesDefaultsToEmptyList() {
    var data = new RawFundPriceModeData("DAILY", "BIDOFFER PRICE", null, 36);
    assertThat(data.availableFundPriceDates()).isEmpty();
  }

  @Test
  void nonNullAvailableFundPriceDatesIsCopiedVerbatim() {
    var dates = List.of(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 5, 18));
    var data = new RawFundPriceModeData("DAILY", "BIDOFFER PRICE", dates, 36);
    assertThat(data.availableFundPriceDates()).containsExactlyElementsOf(dates);
  }

  @Test
  void nullModeThrows() {
    assertThatThrownBy(
            () -> new RawFundPriceModeData(null, "BIDOFFER PRICE", List.of(), 36))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("mode");
  }

  @Test
  void nullFundPriceTypeThrows() {
    assertThatThrownBy(() -> new RawFundPriceModeData("DAILY", null, List.of(), 36))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fundPriceType");
  }
}
