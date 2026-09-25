package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceModeApimDataItem;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Focused tests for the new {@code maxRiskLevel} field threading through {@link
 * TrpGetFundPriceModeApimDataItem} -> {@link FundPriceModeApimContentValidator#normalize} -> the
 * resulting {@code RawFundPriceModeData}.
 *
 * <p>Per the corrected fund-price-apim-contract.md (committed at HEAD 0900848), max-risk-level is a
 * string holding a floating-point value, sibling of the four existing fields, and is deliberately
 * NOT validated: a blank or absent value is legitimate and must not fail the mode fetch. These
 * tests explicitly prove the permissive case (normalize() does not throw), not just the happy path,
 * and confirm the other four fields' validation is unaffected.
 *
 * <p>Lives in the same package as {@link FundPriceModeApimContentValidator} deliberately: that
 * class is package-private with a private constructor and only static methods, confirmed against
 * the live source rather than assumed.
 */
class FundPriceModeApimContentValidatorMaxRiskLevelTest {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private TrpGetFundPriceModeApimDataItem item(
      String mode, String fundPriceType, int months, String maxRiskLevel) {
    return new TrpGetFundPriceModeApimDataItem(
        mode, fundPriceType, List.of("18/09/2026"), months, maxRiskLevel);
  }

  @Test
  void normalizeCarriesTheMaxRiskLevelFieldVerbatimIntoRawFundPriceModeData() {
    var result =
        FundPriceModeApimContentValidator.normalize(
            item("DAILY", "BIDOFFER PRICE", 36, "5"), FORMATTER);

    assertThat(result.maxRiskLevel()).isEqualTo("5");
  }

  @ParameterizedTest
  @ValueSource(strings = {"5", "0.5", "3.25", "0", "10"})
  void normalizeAcceptsVariousFloatingPointStringRepresentations(String value) {
    var result =
        FundPriceModeApimContentValidator.normalize(
            item("DAILY", "BIDOFFER PRICE", 36, value), FORMATTER);

    assertThat(result.maxRiskLevel()).isEqualTo(value);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void normalizeDeliberatelyDoesNotThrowWhenMaxRiskLevelIsBlankOrAbsent(String blankValue) {
    assertThatCode(
            () ->
                FundPriceModeApimContentValidator.normalize(
                    item("DAILY", "BIDOFFER PRICE", 36, blankValue), FORMATTER))
        .as(
            "blank/absent max-risk-level must not fail the mode fetch; only mode/fund-price-type/"
                + "maximum-fund-price-history-months/available-fund-price-dates are validated")
        .doesNotThrowAnyException();
  }

  @Test
  void normalizeStillRejectsInvalidModeRegardlessOfMaxRiskLevelPresence() {
    // Confirms max-risk-level's permissiveness is scoped correctly: it does not accidentally
    // relax validation of the OTHER four fields, which must still be enforced (they throw
    // FundPriceRetrievalException, confirmed against the live normalize() source).
    assertThatCode(
            () ->
                FundPriceModeApimContentValidator.normalize(
                    item("", "BIDOFFER PRICE", 36, "5"), FORMATTER))
        .as("an invalid mode must still fail even though max-risk-level is present and valid")
        .isInstanceOf(
            com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException.class);
  }

  @Test
  void normalizeStillRejectsInvalidMonthsRegardlessOfMaxRiskLevelPresence() {
    assertThatCode(
            () ->
                FundPriceModeApimContentValidator.normalize(
                    item("DAILY", "BIDOFFER PRICE", 0, "5"), FORMATTER))
        .as("a non-positive months value must still fail even with a valid max-risk-level")
        .isInstanceOf(
            com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException.class);
  }
}
