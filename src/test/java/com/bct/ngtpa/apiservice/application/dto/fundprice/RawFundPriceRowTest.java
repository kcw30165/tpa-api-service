package com.bct.ngtpa.apiservice.application.dto.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: {@code RawFundPriceRow} does not exist yet.
 *
 * <p>Carries BOTH bidPrice and offerPrice always -- fundPriceType-driven field selection (bid/offer
 * pair vs single unit price) is a display concern, resolved later in the web mapper, never here.
 * riskLevel is deliberately nullable/blank-legitimate, matching every other risk-level field in
 * this design pack.
 */
class RawFundPriceRowTest {

  private static final LocalDate PRICE_DATE = LocalDate.of(2026, 5, 5);

  private RawFundPriceRow row(String riskLevel) {
    return new RawFundPriceRow(
        "DUSM",
        "Fidelity USD Money Market Fund",
        "Fidelity USD Money Market Fund",
        PRICE_DATE,
        "HKD",
        new BigDecimal("159.7628"),
        new BigDecimal("159.7628"),
        riskLevel);
  }

  @Test
  void carriesAllFieldsVerbatim() {
    var r = row("5");
    assertThat(r.fundCode()).isEqualTo("DUSM");
    assertThat(r.fundName()).isEqualTo("Fidelity USD Money Market Fund");
    assertThat(r.priceDate()).isEqualTo(PRICE_DATE);
    assertThat(r.fundCurrency()).isEqualTo("HKD");
    assertThat(r.bidPrice()).isEqualByComparingTo("159.7628");
    assertThat(r.offerPrice()).isEqualByComparingTo("159.7628");
    assertThat(r.riskLevel()).isEqualTo("5");
  }

  @Test
  void nullRiskLevelIsAcceptedAndNotDefaulted() {
    assertThat(row(null).riskLevel()).isNull();
  }

  @Test
  void blankRiskLevelIsAcceptedVerbatim() {
    assertThat(row("").riskLevel()).isEqualTo("");
  }

  @Test
  void nullFundCodeThrows() {
    assertThatThrownBy(
            () ->
                new RawFundPriceRow(
                    null, "name", "name", PRICE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundCode");
  }

  @Test
  void nullPriceDateThrows() {
    assertThatThrownBy(
            () ->
                new RawFundPriceRow(
                    "DUSM", "name", "name", null, "HKD", BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("priceDate");
  }

  @Test
  void nullBidPriceThrows() {
    assertThatThrownBy(
            () ->
                new RawFundPriceRow(
                    "DUSM", "name", "name", PRICE_DATE, "HKD", null, BigDecimal.ONE, "1"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("bidPrice");
  }

  @Test
  void nullOfferPriceThrows() {
    assertThatThrownBy(
            () ->
                new RawFundPriceRow(
                    "DUSM", "name", "name", PRICE_DATE, "HKD", BigDecimal.ONE, null, "1"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("offerPrice");
  }

  @Test
  void blankFundCodeThrows() {
    assertThatThrownBy(
            () ->
                new RawFundPriceRow(
                    "  ", "name", "name", PRICE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundCode");
  }

  @Test
  void nullFundNameThrows() {
    assertThatThrownBy(
            () ->
                new RawFundPriceRow(
                    "DUSM", null, "name", PRICE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundName");
  }

  @Test
  void blankFundNameThrows() {
    assertThatThrownBy(
            () ->
                new RawFundPriceRow(
                    "DUSM", "  ", "name", PRICE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundName");
  }

  @Test
  void nullFundCurrencyThrows() {
    assertThatThrownBy(
            () ->
                new RawFundPriceRow(
                    "DUSM", "name", "name", PRICE_DATE, null, BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundCurrency");
  }

  @Test
  void blankFundCurrencyThrows() {
    assertThatThrownBy(
            () ->
                new RawFundPriceRow(
                    "DUSM", "name", "name", PRICE_DATE, "  ", BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundCurrency");
  }
}
