package com.bct.ngtpa.apiservice.application.dto.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FundPriceRowResultTest {

  private static final LocalDate PRICE_DATE = LocalDate.of(2026, 5, 5);

  private FundPriceRowResult row(String riskLevel) {
    return new FundPriceRowResult(
        "DUSM",
        "USD Fund",
        "n",
        PRICE_DATE,
        "HKD",
        new BigDecimal("1"),
        new BigDecimal("1"),
        riskLevel);
  }

  @Test
  void carriesAllFieldsVerbatimIncludingBothBidAndOfferAlways() {
    var r = row("5");
    assertThat(r.fundCode()).isEqualTo("DUSM");
    assertThat(r.fundName()).isEqualTo("USD Fund");
    assertThat(r.priceDate()).isEqualTo(PRICE_DATE);
    assertThat(r.fundCurrency()).isEqualTo("HKD");
    assertThat(r.bidPrice()).isEqualByComparingTo("1");
    assertThat(r.offerPrice()).isEqualByComparingTo("1");
    assertThat(r.riskLevel()).isEqualTo("5");
  }

  @Test
  void nullRiskLevelIsAcceptedAndNotDefaulted() {
    assertThat(row(null).riskLevel()).isNull();
  }

  @Test
  void nullFundCodeThrows() {
    assertThatThrownBy(
            () ->
                new FundPriceRowResult(
                    null, "n", "n", PRICE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankFundCodeThrows() {
    assertThatThrownBy(
            () ->
                new FundPriceRowResult(
                    "  ", "n", "n", PRICE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundCode");
  }

  @Test
  void nullFundNameThrows() {
    assertThatThrownBy(
            () ->
                new FundPriceRowResult(
                    "DUSM", null, "n", PRICE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundName");
  }

  @Test
  void blankFundNameThrows() {
    assertThatThrownBy(
            () ->
                new FundPriceRowResult(
                    "DUSM", "  ", "n", PRICE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundName");
  }

  @Test
  void nullPriceDateThrows() {
    assertThatThrownBy(
            () ->
                new FundPriceRowResult(
                    "DUSM", "n", "n", null, "HKD", BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("priceDate");
  }

  @Test
  void nullFundCurrencyThrows() {
    assertThatThrownBy(
            () ->
                new FundPriceRowResult(
                    "DUSM", "n", "n", PRICE_DATE, null, BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundCurrency");
  }

  @Test
  void blankFundCurrencyThrows() {
    assertThatThrownBy(
            () ->
                new FundPriceRowResult(
                    "DUSM", "n", "n", PRICE_DATE, "  ", BigDecimal.ONE, BigDecimal.ONE, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundCurrency");
  }

  @Test
  void nullBidPriceThrows() {
    assertThatThrownBy(
            () ->
                new FundPriceRowResult(
                    "DUSM", "n", "n", PRICE_DATE, "HKD", null, BigDecimal.ONE, "1"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("bidPrice");
  }

  @Test
  void nullOfferPriceThrows() {
    assertThatThrownBy(
            () ->
                new FundPriceRowResult(
                    "DUSM", "n", "n", PRICE_DATE, "HKD", BigDecimal.ONE, null, "1"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("offerPrice");
  }

  @Test
  void carriesChineseFundNameVerbatim() {
    var row =
        new FundPriceRowResult(
            "DUSM",
            "USD Fund",
            "\u7f8e\u5143\u57fa\u91d1",
            PRICE_DATE,
            "HKD",
            BigDecimal.ONE,
            BigDecimal.ONE,
            "5");
    assertThat(row.chineseFundName()).isEqualTo("\u7f8e\u5143\u57fa\u91d1");
  }
}
