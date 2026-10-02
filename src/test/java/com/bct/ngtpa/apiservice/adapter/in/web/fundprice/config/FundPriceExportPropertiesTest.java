package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Startup validation and lookup for fund-price.export.sheet-names. */
class FundPriceExportPropertiesTest {

  @Test
  void defaultsPassValidationAndResolveBothTypes() {
    var properties = new FundPriceExportProperties();
    properties.validate();
    assertThat(properties.sheetName("BIDOFFER PRICE")).isEqualTo("BIDOFFER PRICE");
    assertThat(properties.sheetName("UNIT PRICE")).isEqualTo("UNIT PRICE");
  }

  @Test
  void unknownOrNullTypeHasNoSheetName() {
    var properties = new FundPriceExportProperties();
    assertThat(properties.sheetName("OTHER PRICE")).isNull();
    assertThat(properties.sheetName(null)).isNull();
  }

  @Test
  void missingRequiredTypeFails() {
    var properties = new FundPriceExportProperties();
    properties.getSheetNames().remove("UNIT PRICE");
    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UNIT PRICE");
  }

  @Test
  void nullOrEmptyMapFails() {
    var properties = new FundPriceExportProperties();
    properties.setSheetNames(null);
    assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    properties.setSheetNames(new java.util.LinkedHashMap<>());
    assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void blankSheetNameFails() {
    var properties = new FundPriceExportProperties();
    properties.getSheetNames().put("UNIT PRICE", "  ");
    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void sheetNameLongerThanThirtyOneCharactersFails() {
    var properties = new FundPriceExportProperties();
    properties.getSheetNames().put("UNIT PRICE", "X".repeat(32));
    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("31");
  }

  @Test
  void sheetNameOfExactlyThirtyOneCharactersIsAccepted() {
    var properties = new FundPriceExportProperties();
    properties.getSheetNames().put("UNIT PRICE", "X".repeat(31));
    properties.validate();
    assertThat(properties.sheetName("UNIT PRICE")).hasSize(31);
  }

  @ParameterizedTest
  @ValueSource(strings = {"A[B", "A]B", "A:B", "A*B", "A?B", "A/B", "A\\B"})
  void excelIllegalCharactersFail(String name) {
    var properties = new FundPriceExportProperties();
    properties.getSheetNames().put("BIDOFFER PRICE", name);
    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not contain");
  }

  @Test
  void surroundingWhitespaceIsTrimmed() {
    var properties = new FundPriceExportProperties();
    properties.getSheetNames().put("UNIT PRICE", "  Unit Price  ");
    properties.validate();
    assertThat(properties.sheetName("UNIT PRICE")).isEqualTo("Unit Price");
  }
}
