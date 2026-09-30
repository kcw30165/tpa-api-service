package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceQueryResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Query: risk asc ("0"/""/null last) -> displayed name (per language, case-insensitive) -> unit
 * price -> English name -> fund code. Export: price date desc -> risk asc -> English name -> code.
 */
class FundPriceSortingSupportTest {

  private static final LocalDate DAY = LocalDate.of(2026, 5, 5);
  private final FundPriceSortingSupport support = new FundPriceSortingSupport();

  private static FundPriceRowResult row(
      String code, String name, String chineseName, String risk, String bid, LocalDate date) {
    return new FundPriceRowResult(
        code, name, chineseName, date, "HKD", new BigDecimal(bid), new BigDecimal(bid), risk);
  }

  private static FundPriceRowResult row(String code, String name, String risk, String bid) {
    return row(code, name, null, risk, bid, DAY);
  }

  private List<String> queryOrder(String lang, FundPriceRowResult... rows) {
    return support.sort(new FundPriceQueryResult(List.of(rows), "BIDOFFER PRICE", "5"), lang)
        .rows().stream().map(FundPriceRowResult::fundCode).toList();
  }

  @Test
  void queryOrdersByRiskThenNameThenUnitPrice() {
    assertThat(
            queryOrder(
                "en",
                row("C", "Zebra Fund", "3", "5.0"),
                row("A", "Alpha Fund", "1", "9.0"),
                row("B", "Beta Fund", "1", "2.0"),
                row("D", "Alpha Fund", "1", "1.0")))
        .containsExactly("D", "A", "B", "C");
  }

  @Test
  void queryRanksZeroBlankAndNullRiskLast() {
    assertThat(
            queryOrder(
                "en",
                row("Z", "Zero Fund", "0", "1"),
                row("E", "Empty Fund", "", "1"),
                row("N", "Null Fund", null, "1"),
                row("H", "Half Fund", "0.5", "1"),
                row("F", "Five Fund", "5", "1")))
        .containsExactly("H", "F", "E", "N", "Z");
  }

  @Test
  void queryNameOrderIsCaseInsensitive() {
    assertThat(queryOrder("en", row("B", "Beta Fund", "1", "1"), row("A", "alpha Fund", "1", "1")))
        .containsExactly("A", "B");
  }

  @Test
  void zhHkSortsByDisplayedNameWithEnglishFallbackFirst() {
    // \u4e59 (U+4E59) sorts before \u7532 (U+7532); English fallback names precede Chinese names.
    assertThat(
            queryOrder(
                "zh_HK",
                row("C", "Alpha Fund", "\u7532\u57fa\u91d1", "1", "1", DAY),
                row("A", "Zeta Fund", "\u4e59\u57fa\u91d1", "1", "1", DAY),
                row("B", "Beta Fund", null, "1", "1", DAY)))
        .containsExactly("B", "A", "C");
  }

  @Test
  void englishLanguageIgnoresChineseNames() {
    assertThat(
            queryOrder(
                "en",
                row("C", "Alpha Fund", "\u7532\u57fa\u91d1", "1", "1", DAY),
                row("A", "Zeta Fund", "\u4e59\u57fa\u91d1", "1", "1", DAY)))
        .containsExactly("C", "A");
  }

  @Test
  void identicalDisplayedNamesTieBreakByEnglishNameThenFundCode() {
    assertThat(
            queryOrder(
                "zh_HK",
                row("Y", "Beta Fund", "\u57fa\u91d1", "1", "1", DAY),
                row("X", "Alpha Fund", "\u57fa\u91d1", "1", "1", DAY),
                row("W", "Alpha Fund", "\u57fa\u91d1", "1", "1", DAY)))
        .containsExactly("W", "X", "Y");
  }

  @Test
  void queryKeepsTypeAndMaxRiskLevel() {
    var sorted =
        support.sort(
            new FundPriceQueryResult(List.of(row("A", "Alpha", "1", "1")), "UNIT PRICE", "4"), "en");
    assertThat(sorted.fundPriceType()).isEqualTo("UNIT PRICE");
    assertThat(sorted.maxRiskLevel()).isEqualTo("4");
  }

  @Test
  void exportOrdersByDateDescThenRiskThenEnglishNameThenCode() {
    LocalDate older = DAY.minusDays(1);
    var result =
        new FundPriceExportResult(
            List.of(
                row("OLD", "Alpha Fund", null, "1", "1", older),
                row("ZRO", "Alpha Fund", null, "0", "1", DAY),
                row("BLK", "Beta Fund", null, "", "1", DAY),
                row("LOW", "zeta Fund", null, "1", "1", DAY),
                row("LO2", "Beta Fund", null, "1", "1", DAY),
                row("LO1", "Beta Fund", null, "1", "1", DAY)),
            "UNIT PRICE",
            "5",
            BigInteger.TWO);

    var sorted = support.sort(result);

    assertThat(sorted.rows())
        .extracting(FundPriceRowResult::fundCode)
        .containsExactly("LO1", "LO2", "LOW", "ZRO", "BLK", "OLD");
    assertThat(sorted.fundPriceType()).isEqualTo("UNIT PRICE");
    assertThat(sorted.maxRiskLevel()).isEqualTo("5");
    assertThat(sorted.unitPricePrecision()).isEqualTo(BigInteger.TWO);
  }

  @Test
  void exportSortKeepsMode() {
    var result =
        new FundPriceExportResult(
            List.of(row("A", "Alpha Fund", null, "1", "1", DAY)),
            "UNIT PRICE",
            "5",
            BigInteger.TWO,
            "MONTHLY");
    assertThat(support.sort(result).mode()).isEqualTo("MONTHLY");
  }
}
