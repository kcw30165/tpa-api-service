package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonOptionsResponse;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData.RawFundComparisonOption;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: FundComparisonOptionsMapper, FundComparisonOptionsResponse,
 * FundComparisonOptionResponse, and FundComparisonOptionsResultResponse do not exist yet.
 *
 * <p>Confirmed per script 033's evidence: fund-name selection reuses {@code
 * FundPriceWebMapper.displayFundName}'s exact rule (zh_HK uses chinFundName when non-blank, else
 * fundName; any other language always uses fundName), re-expressed against {@link
 * RawFundComparisonOption} -- an accepted, small, intentional duplication (Nick's confirmed
 * decision) since there is no shared interface with {@code FundPriceRowResult}. Sorting is
 * case-insensitive display-name ascending then fund code -- a NEW small comparator, not {@code
 * FundPriceSortingSupport} reused directly, since risk-level/price tie-breakers do not apply to
 * options data. Time-range normalization delegates entirely to {@code
 * FundComparisonTimeRangeParser} (Milestone 1); this mapper does not reimplement grammar
 * validation, dedup, or default-selection.
 */
class FundComparisonOptionsMapperTest {

  private final FundComparisonOptionsMapper mapper = new FundComparisonOptionsMapper();

  private RawFundComparisonOptionsData raw(String rawTimeRange, RawFundComparisonOption... funds) {
    return new RawFundComparisonOptionsData(rawTimeRange, List.of(funds));
  }

  private RawFundComparisonOption fund(String code, String name, String chinName) {
    return new RawFundComparisonOption(code, name, chinName);
  }

  // ----- fund-name selection ---------------------------------------------------
  @Test
  void englishLanguageAlwaysUsesTheEnglishFundName() {
    var data =
        raw(
            "10D",
            fund(
                "FAEU",
                "Fidelity Advantage Portfolio Fund",
                "\u5bcc\u9054\u512a\u8d8a\u6295\u8cc7\u7d44\u5408"));

    var response = mapper.toResponse(data, "en");

    assertThat(response.funds()).hasSize(1);
    assertThat(response.funds().get(0).fundName()).isEqualTo("Fidelity Advantage Portfolio Fund");
  }

  @Test
  void zhHkLanguageUsesTheChineseFundNameWhenPresent() {
    var data =
        raw(
            "10D",
            fund(
                "FAEU",
                "Fidelity Advantage Portfolio Fund",
                "\u5bcc\u9054\u512a\u8d8a\u6295\u8cc7\u7d44\u5408"));

    var response = mapper.toResponse(data, "zh_HK");

    assertThat(response.funds().get(0).fundName())
        .isEqualTo("\u5bcc\u9054\u512a\u8d8a\u6295\u8cc7\u7d44\u5408");
  }

  @Test
  void zhHkFallsBackToEnglishFundNameWhenChineseNameIsBlank() {
    var data = raw("10D", fund("FBAU", "Some English Name", "   "));

    var response = mapper.toResponse(data, "zh_HK");

    assertThat(response.funds().get(0).fundName()).isEqualTo("Some English Name");
  }

  @Test
  void zhHkFallsBackToEnglishFundNameWhenChineseNameIsEmpty() {
    var data = raw("10D", fund("FBAU", "Some English Name", ""));

    var response = mapper.toResponse(data, "zh_HK");

    assertThat(response.funds().get(0).fundName()).isEqualTo("Some English Name");
  }

  // ----- public response never exposes chinFundName ----------------------------
  @Test
  void publicFundResponseOnlyExposesFundCodeAndFundName() {
    var data = raw("10D", fund("FAEU", "English Name", "Chinese Name"));

    var response = mapper.toResponse(data, "en");

    assertThat(response.funds().get(0).fundCode()).isEqualTo("FAEU");
    assertThat(response.funds().get(0).fundName()).isEqualTo("English Name");
  }

  // ----- sorting: case-insensitive display-name ascending, then fund code ------
  @Test
  void fundsAreSortedByDisplayNameCaseInsensitiveAscending() {
    var data =
        raw(
            "10D",
            fund("FBAU", "banana Fund", ""),
            fund("FAEU", "Apple Fund", ""),
            fund("FCAU", "Cherry Fund", ""));

    var response = mapper.toResponse(data, "en");

    assertThat(response.funds()).extracting("fundCode").containsExactly("FAEU", "FBAU", "FCAU");
  }

  @Test
  void tiedDisplayNamesAreBrokenByFundCodeAscending() {
    var data = raw("10D", fund("FZZZ", "Same Name", ""), fund("FAAA", "Same Name", ""));

    var response = mapper.toResponse(data, "en");

    assertThat(response.funds()).extracting("fundCode").containsExactly("FAAA", "FZZZ");
  }

  @Test
  void sortingUsesTheDisplayNameForTheRequestedLanguageNotAlwaysEnglish() {
    // English names alone would sort "Apple" before "Zulu" -- but with zh_HK, sorting
    // must use the CHINESE display name instead, which produces a DIFFERENT order:
    // U+4E59 (\u4e59) < U+7532 (\u7532) by code point (confirmed: 0x4e59 < 0x7532), so
    // the English-name-based intuition (F2/Apple before F1/Zulu) is wrong here -- the
    // zh_HK sort must put F1 first because its Chinese name has the smaller code point,
    // even though its English name ("Zulu") would otherwise sort last.
    var data =
        raw("10D", fund("F1", "Zulu English", "\u4e59"), fund("F2", "Apple English", "\u7532"));

    var response = mapper.toResponse(data, "zh_HK");

    assertThat(response.funds()).extracting("fundCode").containsExactly("F1", "F2");
  }

  // ----- time-range normalization: delegates to FundComparisonTimeRangeParser ---
  @Test
  void validTimeRangeStringIsNormalizedIntoTimeRangesAndDefaultTimeRange() {
    var data = raw("10D,1M,3M,6M,1Y,YTD,3Y", fund("FAEU", "Name", ""));

    var response = mapper.toResponse(data, "en");

    assertThat(response.timeRanges()).containsExactly("10D", "1M", "3M", "6M", "1Y", "YTD", "3Y");
    assertThat(response.defaultTimeRange()).isEqualTo("10D");
  }

  @Test
  void invalidTokensAreDroppedDuringNormalization() {
    var data = raw("10D,bogus,1M", fund("FAEU", "Name", ""));

    var response = mapper.toResponse(data, "en");

    assertThat(response.timeRanges()).containsExactly("10D", "1M");
  }

  @Test
  void allInvalidTimeRangeTokensPropagatesFundComparisonRetrievalException() {
    var data = raw("bogus,also-bogus", fund("FAEU", "Name", ""));

    assertThatThrownBy(() -> mapper.toResponse(data, "en"))
        .isInstanceOf(FundComparisonRetrievalException.class);
  }

  // ----- full shape sanity check -------------------------------------------------
  @Test
  void resultRecordExposesExpectedPublicShape() {
    var data = raw("10D,1M", fund("FAEU", "Name", ""));

    FundComparisonOptionsResponse response = mapper.toResponse(data, "en");

    assertThat(response.timeRanges()).containsExactly("10D", "1M");
    assertThat(response.defaultTimeRange()).isEqualTo("10D");
    assertThat(response.funds()).hasSize(1);
  }
}
