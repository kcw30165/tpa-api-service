package com.bct.ngtpa.apiservice.application.dto.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: {@code FundPriceModeSessionMetadata} does not exist yet.
 *
 * <p>Deliberately mirrors {@link RawFundPriceModeData}'s exact 5-field shape (same names,
 * same types) -- this is a straight pass-through cache of a validated mode fetch result, not an
 * independently-designed shape. {@code mode}/{@code fundPriceType} are required (matching
 * RawFundPriceModeData's own null-checks); {@code maxRiskLevel} is deliberately NOT validated,
 * per fund-price-apim-contract.md -- null and blank are both legitimate.
 */
class FundPriceModeSessionMetadataTest {

  private static final List<LocalDate> DATES =
      List.of(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 5, 18));

  private FundPriceModeSessionMetadata metadata(String maxRiskLevel) {
    return new FundPriceModeSessionMetadata("DAILY", "BIDOFFER PRICE", DATES, 36, maxRiskLevel);
  }

  @Test
  void carriesAllFiveFieldsVerbatim() {
    var m = metadata("5");
    assertThat(m.mode()).isEqualTo("DAILY");
    assertThat(m.fundPriceType()).isEqualTo("BIDOFFER PRICE");
    assertThat(m.availableFundPriceDates()).containsExactlyElementsOf(DATES);
    assertThat(m.maximumFundPriceHistoryMonths()).isEqualTo(36);
    assertThat(m.maxRiskLevel()).isEqualTo("5");
  }

  @Test
  void nullModeThrows() {
    assertThatThrownBy(
            () -> new FundPriceModeSessionMetadata(null, "BIDOFFER PRICE", DATES, 36, "5"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("mode");
  }

  @Test
  void nullFundPriceTypeThrows() {
    assertThatThrownBy(() -> new FundPriceModeSessionMetadata("DAILY", null, DATES, 36, "5"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fundPriceType");
  }

  @Test
  void nullAvailableFundPriceDatesDefaultsToEmptyList() {
    var m = new FundPriceModeSessionMetadata("DAILY", "BIDOFFER PRICE", null, 36, "5");
    assertThat(m.availableFundPriceDates()).isEmpty();
  }

  @Test
  void nullMaxRiskLevelIsAcceptedAndNotDefaulted() {
    // Deliberately NOT validated/defaulted, per fund-price-apim-contract.md: null is legitimate.
    var m = metadata(null);
    assertThat(m.maxRiskLevel()).isNull();
  }

  @Test
  void blankMaxRiskLevelIsAcceptedVerbatim() {
    var m = metadata("");
    assertThat(m.maxRiskLevel()).isEqualTo("");
  }

  @Test
  void fieldShapeMirrorsRawFundPriceModeDataFieldCount() {
    // Confirms the deliberate design constraint: exactly 5 fields, same as
    // RawFundPriceModeData, not an independently-invented shape.
    var recordComponents = FundPriceModeSessionMetadata.class.getRecordComponents();
    assertThat(recordComponents).hasSize(5);
  }
}
