package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceModeApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceModeData;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Closes the coverage gap identified by script 030's JaCoCo XML parse: every multi-condition {@code
 * ||} guard in this package-private validator had exactly one missing branch. The 023 adapter test
 * exercised the blank/unsupported side of each guard but never the genuinely {@code null} side,
 * since the adapter's own DTO fields were always constructed with real string values. This test
 * exercises the validator directly with explicit nulls. DTO/validator tier: targets 95% instruction
 * / 95% branch.
 */
class FundPriceModeApimContentValidatorTest {

  private static final DateTimeFormatter FORMATTER =
      new com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.FundPriceDateFormatProperties()
          .fullDateFormatter();

  private static TrpGetFundPriceModeApimDataItem item(
      String mode, String fundPriceType, List<String> dates, int maxMonths) {
    return new TrpGetFundPriceModeApimDataItem(mode, fundPriceType, dates, maxMonths, null);
  }

  // ----- requireSingleItem: the null-item-within-a-non-null-list branch -------

  @Test
  void nullDataListFailsClosed() {
    assertThatThrownBy(() -> FundPriceModeApimContentValidator.requireSingleItem(null))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void singleNullItemWithinNonNullListFailsClosed() {
    // List.of() forbids null elements, so Arrays.asList is required to construct this exact case.
    List<TrpGetFundPriceModeApimDataItem> withNullItem =
        Arrays.asList((Object[]) new TrpGetFundPriceModeApimDataItem[] {null}).stream()
            .map(o -> (TrpGetFundPriceModeApimDataItem) o)
            .toList();
    assertThatThrownBy(() -> FundPriceModeApimContentValidator.requireSingleItem(withNullItem))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  // ----- normalize: the explicit-null side of every guard, not just blank -----

  @Test
  void nullModeFailsClosed() {
    var withNullMode = item(null, "BIDOFFER PRICE", List.of("18/09/2026"), 36);
    assertThatThrownBy(() -> FundPriceModeApimContentValidator.normalize(withNullMode, FORMATTER))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void nullFundPriceTypeFailsClosed() {
    var withNullType = item("DAILY", null, List.of("18/09/2026"), 36);
    assertThatThrownBy(() -> FundPriceModeApimContentValidator.normalize(withNullType, FORMATTER))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void nullAvailableDatesListFailsClosed() {
    var withNullDates = item("DAILY", "BIDOFFER PRICE", null, 36);
    assertThatThrownBy(() -> FundPriceModeApimContentValidator.normalize(withNullDates, FORMATTER))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void nullSingleAvailableDateEntryFailsClosed() {
    var withNullEntry = item("DAILY", "BIDOFFER PRICE", Arrays.asList("18/09/2026", null), 36);
    assertThatThrownBy(() -> FundPriceModeApimContentValidator.normalize(withNullEntry, FORMATTER))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  // ----- confirm the successful path still normalizes correctly ---------------

  @Test
  void validItemNormalizesSuccessfully() {
    var valid = item("MONTHLY", "UNIT PRICE", List.of("18/09/2026"), 12);
    RawFundPriceModeData result = FundPriceModeApimContentValidator.normalize(valid, FORMATTER);
    assertThat(result.mode()).isEqualTo("MONTHLY");
    assertThat(result.fundPriceType()).isEqualTo("UNIT PRICE");
    assertThat(result.availableFundPriceDates()).containsExactly(LocalDate.of(2026, 9, 18));
    assertThat(result.maximumFundPriceHistoryMonths()).isEqualTo(12);
  }
}
