package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.request.FundComparisonPricesRequest;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonPricesResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceRow;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRequestInvalidException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Fail-first test for Fund Comparison Milestone 3, sub-task 3.2 (request validation + response
 * mapping for {@code POST /api/v1/fund-comparison/prices}), per
 * docs/design/13-fund-comparison/04-validation-and-error-handling.md section 4.3 and
 * 02-api-contract.md sections 2.3-2.4.
 *
 * <p>The mapper is constructed with a plain {@code dd/MM/yyyy} {@link DateTimeFormatter} here
 * (rather than the production {@code FundPriceDateFormatProperties} helper) to keep this unit test
 * self-contained; the production bean wired in via {@code fundPriceFullDateFormatter} formats
 * identically, per docs/design/13-fund-comparison/02-api-contract.md section 2.1 ("All public date
 * fields ... use dd/MM/yyyy").
 */
class FundComparisonPricesWebMapperTest {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private FundComparisonPricesWebMapper newMapper() {
    return new FundComparisonPricesWebMapper(FORMATTER);
  }

  // ----- validate(): fund codes -----

  @Test
  void nullRequestThrowsRequestInvalid() {
    var mapper = newMapper();
    assertThatThrownBy(() -> mapper.validate(null))
        .isInstanceOf(FundComparisonRequestInvalidException.class);
  }

  @Test
  void missingFundCodesThrowsRequestInvalid() {
    var mapper = newMapper();
    var request = new FundComparisonPricesRequest(null, "10D");
    assertThatThrownBy(() -> mapper.validate(request))
        .isInstanceOf(FundComparisonRequestInvalidException.class)
        .hasMessageContaining("fund code");
  }

  @Test
  void blankFundCodeEntryThrowsRequestInvalid() {
    var mapper = newMapper();
    var request = new FundComparisonPricesRequest(List.of("FAEU", " "), "10D");
    assertThatThrownBy(() -> mapper.validate(request))
        .isInstanceOf(FundComparisonRequestInvalidException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void nullFundCodeEntryThrowsRequestInvalid() {
    var mapper = newMapper();
    var codes = new ArrayList<String>();
    codes.add("FAEU");
    codes.add(null);
    var request = new FundComparisonPricesRequest(codes, "10D");
    assertThatThrownBy(() -> mapper.validate(request))
        .isInstanceOf(FundComparisonRequestInvalidException.class);
  }

  @Test
  void moreThanFiveUniqueFundCodesThrowsRequestInvalid() {
    var mapper = newMapper();
    var request = new FundComparisonPricesRequest(List.of("A", "B", "C", "D", "E", "F"), "10D");
    assertThatThrownBy(() -> mapper.validate(request))
        .isInstanceOf(FundComparisonRequestInvalidException.class)
        .hasMessageContaining("5");
  }

  @Test
  void exactlyFiveUniqueFundCodesIsAccepted() {
    var request = new FundComparisonPricesRequest(List.of("A", "B", "C", "D", "E"), "10D");
    var validated = newMapper().validate(request);
    assertThat(validated.fundCodes()).hasSize(5);
  }

  @Test
  void duplicateFundCodesAreDeduplicatedPreservingFirstOccurrenceOrder() {
    var request = new FundComparisonPricesRequest(List.of("FAEU", "FBAU", "FAEU"), "10D");
    var validated = newMapper().validate(request);
    assertThat(validated.fundCodes()).containsExactly("FAEU", "FBAU");
  }

  // ----- validate(): time range -----

  @Test
  void nullTimeRangeThrowsRequestInvalid() {
    var mapper = newMapper();
    var request = new FundComparisonPricesRequest(List.of("FAEU"), null);
    assertThatThrownBy(() -> mapper.validate(request))
        .isInstanceOf(FundComparisonRequestInvalidException.class)
        .hasMessageContaining("timeRange");
  }

  @Test
  void blankTimeRangeThrowsRequestInvalid() {
    var mapper = newMapper();
    var request = new FundComparisonPricesRequest(List.of("FAEU"), " ");
    assertThatThrownBy(() -> mapper.validate(request))
        .isInstanceOf(FundComparisonRequestInvalidException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"10X", "-1D", "1.5M", "0D", "abc", "D10", "YTDD"})
  void syntacticallyInvalidTimeRangeTokensThrowRequestInvalid(String invalidToken) {
    var mapper = newMapper();
    var request = new FundComparisonPricesRequest(List.of("FAEU"), invalidToken);
    assertThatThrownBy(() -> mapper.validate(request))
        .isInstanceOf(FundComparisonRequestInvalidException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"10D", "1M", "3M", "6M", "1Y", "YTD", "3Y"})
  void validTimeRangeTokensAreAccepted(String validToken) {
    var request = new FundComparisonPricesRequest(List.of("FAEU"), validToken);
    var validated = newMapper().validate(request);
    assertThat(validated.timeRange()).isEqualTo(validToken);
  }

  // ----- toResponse(): mapping -----

  private RawFundPriceRow row(String fundCode, LocalDate date, String bid) {
    return new RawFundPriceRow(
        fundCode,
        "Fund " + fundCode,
        "Fund " + fundCode,
        date,
        "HKD",
        new BigDecimal(bid),
        new BigDecimal("999.9999"),
        "3");
  }

  @Test
  void mapsBidPriceToUnitPriceAndNeverExposesOfferPrice() {
    var rawData = new RawFundPriceData(List.of(row("FAEU", LocalDate.of(2026, 10, 6), "159.7628")));
    var result =
        new FundComparisonPricesResult(
            LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 15), List.of("FAEU"), rawData);
    var response = newMapper().toResponse(result);
    assertThat(response.series()).hasSize(1);
    var point = response.series().get(0).prices().get(0);
    assertThat(point.unitPrice()).isEqualByComparingTo("159.7628");
    // offerPrice (999.9999) must never appear anywhere in the response.
    assertThat(response.toString()).doesNotContain("999.9999");
  }

  @Test
  void formatsFromDateAndToDateUsingThePublicFormatter() {
    var result =
        new FundComparisonPricesResult(
            LocalDate.of(2026, 10, 6),
            LocalDate.of(2026, 10, 15),
            List.of("FAEU"),
            new RawFundPriceData(List.of()));
    var response = newMapper().toResponse(result);
    assertThat(response.fromDate()).isEqualTo("06/10/2026");
    assertThat(response.toDate()).isEqualTo("15/10/2026");
  }

  @Test
  void ordersPricePointsByDateAscendingWithinASeries() {
    var rawData =
        new RawFundPriceData(
            List.of(
                row("FAEU", LocalDate.of(2026, 10, 10), "2"),
                row("FAEU", LocalDate.of(2026, 10, 6), "1"),
                row("FAEU", LocalDate.of(2026, 10, 8), "1.5")));
    var result =
        new FundComparisonPricesResult(
            LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 15), List.of("FAEU"), rawData);
    var response = newMapper().toResponse(result);
    var prices = response.series().get(0).prices();
    assertThat(prices)
        .extracting("priceDate")
        .containsExactly("06/10/2026", "08/10/2026", "10/10/2026");
  }

  @Test
  void groupsRowsByFundCodeInRequestOrderOmittingFundsWithNoRows() {
    // Requested order is FAEU then FBAU, but raw data only contains rows for FBAU: the series
    // must contain exactly one entry (FBAU) -- FAEU is omitted entirely, not included with an
    // empty prices array, per docs/design/13-fund-comparison/04-validation-and-error-handling.md
    // section 4.5's "legitimate empty price result" example (series: [] when nothing matches).
    var rawData = new RawFundPriceData(List.of(row("FBAU", LocalDate.of(2026, 10, 6), "1")));
    var result =
        new FundComparisonPricesResult(
            LocalDate.of(2026, 10, 6),
            LocalDate.of(2026, 10, 15),
            List.of("FAEU", "FBAU"),
            rawData);
    var response = newMapper().toResponse(result);
    assertThat(response.series()).hasSize(1);
    assertThat(response.series().get(0).fundCode()).isEqualTo("FBAU");
  }

  @Test
  void emptyRawDataAcrossAllFundsProducesEmptyTopLevelSeries() {
    var result =
        new FundComparisonPricesResult(
            LocalDate.of(2026, 10, 6),
            LocalDate.of(2026, 10, 15),
            List.of("FAEU", "FBAU"),
            new RawFundPriceData(List.of()));
    var response = newMapper().toResponse(result);
    assertThat(response.series()).isEmpty();
  }

  @Test
  void seriesPreservesRequestFirstOccurrenceOrderNotRawDataOrder() {
    // Raw data arrives in FBAU-then-FAEU order, but the request asked for FAEU-then-FBAU: the
    // response series must follow the REQUEST's order.
    var rawData =
        new RawFundPriceData(
            List.of(
                row("FBAU", LocalDate.of(2026, 10, 6), "1"),
                row("FAEU", LocalDate.of(2026, 10, 6), "1")));
    var result =
        new FundComparisonPricesResult(
            LocalDate.of(2026, 10, 6),
            LocalDate.of(2026, 10, 15),
            List.of("FAEU", "FBAU"),
            rawData);
    var response = newMapper().toResponse(result);
    assertThat(response.series()).extracting("fundCode").containsExactly("FAEU", "FBAU");
  }

  @Test
  void nullResultThrowsNullPointerException() {
    var mapper = newMapper();
    assertThatThrownBy(() -> mapper.toResponse(null)).isInstanceOf(NullPointerException.class);
  }
}
