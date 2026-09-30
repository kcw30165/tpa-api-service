package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the STRICT + "yyyy" defect: the production formatter must parse the real
 * TRPGetFundPriceMode / FE dates, and startup validation must reject patterns that format but
 * cannot parse back under strict resolution.
 */
class FundPriceDateFormatPropertiesTest {

  private static FundPriceDateFormatProperties withPattern(String pattern) {
    FundPriceDateFormatProperties properties = new FundPriceDateFormatProperties();
    properties.setFullDate(pattern);
    return properties;
  }

  @Test
  void defaultPatternIsUuuuAndPassesValidation() {
    FundPriceDateFormatProperties properties = new FundPriceDateFormatProperties();
    properties.validate();
    assertThat(properties.getFullDate()).isEqualTo("dd/MM/uuuu");
  }

  @Test
  void productionFormatterParsesRealApimDatesStrictly() {
    FundPriceDateFormatProperties properties = new FundPriceDateFormatProperties();
    properties.validate();
    DateTimeFormatter formatter = properties.fullDateFormatter();

    assertThat(LocalDate.parse("10/07/2026", formatter)).isEqualTo(LocalDate.of(2026, 7, 10));
    assertThat(LocalDate.parse("29/02/2028", formatter)).isEqualTo(LocalDate.of(2028, 2, 29));
    assertThat(LocalDate.of(2026, 9, 29).format(formatter)).isEqualTo("29/09/2026");
  }

  @Test
  void productionFormatterStillRejectsImpossibleDates() {
    DateTimeFormatter formatter = new FundPriceDateFormatProperties().fullDateFormatter();
    assertThatThrownBy(() -> LocalDate.parse("31/02/2026", formatter))
        .isInstanceOf(DateTimeParseException.class);
    assertThatThrownBy(() -> LocalDate.parse("29/02/2026", formatter))
        .isInstanceOf(DateTimeParseException.class);
  }

  @Test
  void yearOfEraPatternFailsStartupValidation() {
    assertThatThrownBy(() -> withPattern("dd/MM/yyyy").validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("uuuu");
  }

  @Test
  void patternWithoutYearFailsStartupValidation() {
    assertThatThrownBy(() -> withPattern("dd/MM").validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot be parsed back");
  }

  @Test
  void timeOnlyPatternFailsStartupValidation() {
    assertThatThrownBy(() -> withPattern("HH:mm").validate())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void twoDigitYearPatternFailsRoundTrip() {
    assertThatThrownBy(() -> withPattern("dd/MM/uu").validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not round-trip");
  }

  @Test
  void reservedPatternCharacterFailsStartupValidation() {
    assertThatThrownBy(() -> withPattern("dd/MM/uuuu{").validate())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankAndNullPatternsFailStartupValidation() {
    assertThatThrownBy(() -> withPattern(" ").validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not be blank");
    assertThatThrownBy(() -> withPattern(null).validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void surroundingWhitespaceIsTrimmed() {
    FundPriceDateFormatProperties properties = withPattern("  dd/MM/uuuu  ");
    properties.validate();
    assertThat(properties.getFullDate()).isEqualTo("dd/MM/uuuu");
  }

  private static FundPriceDateFormatProperties withMonth(String pattern) {
    FundPriceDateFormatProperties properties = new FundPriceDateFormatProperties();
    properties.setMonth(pattern);
    return properties;
  }

  @Test
  void defaultMonthPatternFormatsMonthAndYear() {
    FundPriceDateFormatProperties properties = new FundPriceDateFormatProperties();
    properties.validate();
    assertThat(properties.getMonth()).isEqualTo("MM/uuuu");
    assertThat(LocalDate.of(2026, 9, 2).format(properties.monthFormatter())).isEqualTo("09/2026");
  }

  @Test
  void monthYearOfEraPatternFailsStartupValidation() {
    assertThatThrownBy(() -> withMonth("MM/yyyy").validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fund-price.date-format.month")
        .hasMessageContaining("uuuu");
  }

  @Test
  void monthPatternWithDayOfMonthFails() {
    assertThatThrownBy(() -> withMonth("dd/MM/uuuu").validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("day of month");
  }

  @Test
  void monthTwoDigitYearFailsRoundTrip() {
    assertThatThrownBy(() -> withMonth("MM/uu").validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not round-trip");
  }

  @Test
  void monthPatternWithoutYearFails() {
    assertThatThrownBy(() -> withMonth("MM").validate())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void blankAndNullMonthPatternsFail() {
    assertThatThrownBy(() -> withMonth(" ").validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fund-price.date-format.month must not be blank");
    assertThatThrownBy(() -> withMonth(null).validate())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void monthSurroundingWhitespaceIsTrimmed() {
    FundPriceDateFormatProperties properties = withMonth("  MM/uuuu  ");
    properties.validate();
    assertThat(properties.getMonth()).isEqualTo("MM/uuuu");
  }
}
