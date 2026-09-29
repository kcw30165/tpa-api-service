package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.mapper;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response.FundPriceQueryResponse;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceQueryResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first tests for FundPriceWebMapper: bid/offer-vs-unit field selection driven by
 * fundPriceType, priceDate formatted via the shared fundPriceFullDateFormatter bean (dd/MM/yyyy,
 * per the API contract's own "05/05/2026" example -- NOT ISO).
 */
class FundPriceWebMapperTest {

  private static final DateTimeFormatter FORMATTER = new com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.FundPriceDateFormatProperties().fullDateFormatter();

  private FundPriceWebMapper newMapper() {
    return new FundPriceWebMapper(FORMATTER);
  }

  private FundPriceRowResult row(String riskLevel) {
    return new FundPriceRowResult(
        "DUSM", "USD Fund", "\u7f8e\u5143\u57fa\u91d1", LocalDate.of(2026, 5, 5), "HKD",
        new BigDecimal("1.1"), new BigDecimal("1.2"), riskLevel);
  }

  @Test
  void bidOfferPriceTypeProducesBidAndOfferFieldsNotUnitPrice() {
    var result = new FundPriceQueryResult(List.of(row("5")), "BIDOFFER PRICE", "5");

    FundPriceQueryResponse response = newMapper().toQueryResponse(result, "en");

    var mapped = response.rows().get(0);
    assertThat(mapped.bidUnitPrice()).isEqualByComparingTo("1.1");
    assertThat(mapped.offerUnitPrice()).isEqualByComparingTo("1.2");
    assertThat(mapped.unitPrice()).isNull();
  }

  @Test
  void unitPriceTypeProducesUnitPriceFieldSourcedFromBidPriceNotOfferPrice() {
    var result = new FundPriceQueryResult(List.of(row("5")), "UNIT PRICE", "5");

    FundPriceQueryResponse response = newMapper().toQueryResponse(result, "en");

    var mapped = response.rows().get(0);
    assertThat(mapped.unitPrice()).isEqualByComparingTo("1.1"); // sourced from bid-price
    assertThat(mapped.bidUnitPrice()).isNull();
    assertThat(mapped.offerUnitPrice()).isNull();
  }

  @Test
  void priceDateIsFormattedAsDdMmYyyyNotIso() {
    var result = new FundPriceQueryResult(List.of(row("5")), "BIDOFFER PRICE", "5");

    FundPriceQueryResponse response = newMapper().toQueryResponse(result, "en");

    assertThat(response.rows().get(0).priceDate()).isEqualTo("05/05/2026");
  }

  @Test
  void maxRiskLevelIsCarriedThroughVerbatim() {
    var result = new FundPriceQueryResult(List.of(row("5")), "BIDOFFER PRICE", "5");

    assertThat(newMapper().toQueryResponse(result, "en").maxRiskLevel()).isEqualTo("5");
  }

  @Test
  void nullMaxRiskLevelIsCarriedThroughAsNull() {
    var result = new FundPriceQueryResult(List.of(row(null)), "BIDOFFER PRICE", null);

    assertThat(newMapper().toQueryResponse(result, "en").maxRiskLevel()).isNull();
  }

  @Test
  void exportResultAlsoMapsRowsCorrectly() {
    // FundPriceExportResult mirrors FundPriceQueryResult's shape exactly; the mapper must handle
    // both, since the export path also needs mapped rows for the XLSX exporter (a later stage).
    var exportRow =
        new com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult(
            "DUSM", "USD Fund", "n", LocalDate.of(2026, 5, 5), "HKD", new BigDecimal("1.1"), new BigDecimal("1.2"), "5");
    var result = new FundPriceExportResult(List.of(exportRow), "UNIT PRICE", "5", BigInteger.TWO);

    var mappedRows = newMapper().toExportRows(result);

    assertThat(mappedRows).hasSize(1);
    assertThat(mappedRows.get(0).unitPrice()).isEqualByComparingTo("1.1");
  }
  @Test
  void englishLanguageSelectsEnglishFundName() {
    var result = new FundPriceQueryResult(List.of(row("5")), "BIDOFFER PRICE", "5");

    var response = newMapper().toQueryResponse(result, "en");

    assertThat(response.rows().get(0).fundName()).isEqualTo("USD Fund");
  }

  @Test
  void zhHkLanguageSelectsChineseFundName() {
    var result = new FundPriceQueryResult(List.of(row("5")), "BIDOFFER PRICE", "5");

    var response = newMapper().toQueryResponse(result, "zh_HK");

    assertThat(response.rows().get(0).fundName()).isEqualTo("\u7f8e\u5143\u57fa\u91d1");
  }

  @Test
  void zhHkFallsBackToEnglishNameWhenChineseNameMissing() {
    var noChinese =
        new FundPriceRowResult(
            "DUSM", "USD Fund", null, LocalDate.of(2026, 5, 5), "HKD",
            new BigDecimal("1.1"), new BigDecimal("1.2"), "5");
    var result = new FundPriceQueryResult(List.of(noChinese), "BIDOFFER PRICE", "5");

    assertThat(newMapper().toQueryResponse(result, "zh_HK").rows().get(0).fundName())
        .isEqualTo("USD Fund");
  }

  @Test
  void displayFundNameUsesEnglishForBlankChineseName() {
    var blankChinese =
        new FundPriceRowResult(
            "DUSM", "USD Fund", "  ", LocalDate.of(2026, 5, 5), "HKD",
            BigDecimal.ONE, BigDecimal.ONE, "5");

    assertThat(FundPriceWebMapper.displayFundName(blankChinese, "zh_HK")).isEqualTo("USD Fund");
    assertThat(FundPriceWebMapper.displayFundName(row("5"), "en")).isEqualTo("USD Fund");
  }
}
