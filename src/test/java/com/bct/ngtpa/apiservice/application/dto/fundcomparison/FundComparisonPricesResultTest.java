package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first test for the Milestone 3 prices use-case output (sub-task 3.3's return type, consumed
 * by {@code FundComparisonPricesWebMapper#toResponse}, sub-task 3.2), per
 * docs/design/13-fund-comparison/01-solution-design.md section 1.4 and 02-api-contract.md section
 * 2.3.
 */
class FundComparisonPricesResultTest {

  private static final LocalDate FROM_DATE = LocalDate.of(2026, 10, 6);
  private static final LocalDate TO_DATE = LocalDate.of(2026, 10, 15);

  @Test
  void carriesAllFieldsVerbatim() {
    var rawData = new RawFundPriceData(List.of());
    var result =
        new FundComparisonPricesResult(FROM_DATE, TO_DATE, List.of("FAEU", "FBAU"), rawData);
    assertThat(result.fromDate()).isEqualTo(FROM_DATE);
    assertThat(result.toDate()).isEqualTo(TO_DATE);
    assertThat(result.fundCodes()).containsExactly("FAEU", "FBAU");
    assertThat(result.rawData()).isSameAs(rawData);
  }

  @Test
  void nullFromDateThrows() {
    var fundCodes = List.of("FAEU");
    var rawData = new RawFundPriceData(List.of());
    assertThatThrownBy(() -> new FundComparisonPricesResult(null, TO_DATE, fundCodes, rawData))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fromDate");
  }

  @Test
  void nullToDateThrows() {
    var fundCodes = List.of("FAEU");
    var rawData = new RawFundPriceData(List.of());
    assertThatThrownBy(() -> new FundComparisonPricesResult(FROM_DATE, null, fundCodes, rawData))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("toDate");
  }

  @Test
  void fromDateAfterToDateThrows() {
    var fundCodes = List.of("FAEU");
    var rawData = new RawFundPriceData(List.of());
    assertThatThrownBy(() -> new FundComparisonPricesResult(TO_DATE, FROM_DATE, fundCodes, rawData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fromDate");
  }

  @Test
  void nullFundCodesThrows() {
    var rawData = new RawFundPriceData(List.of());
    assertThatThrownBy(() -> new FundComparisonPricesResult(FROM_DATE, TO_DATE, null, rawData))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fundCodes");
  }

  @Test
  void nullRawDataThrows() {
    var fundCodes = List.of("FAEU");
    assertThatThrownBy(() -> new FundComparisonPricesResult(FROM_DATE, TO_DATE, fundCodes, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("rawData");
  }

  @Test
  void fundCodesListIsImmutable() {
    var mutable = new ArrayList<String>();
    mutable.add("FAEU");
    var result =
        new FundComparisonPricesResult(
            FROM_DATE, TO_DATE, mutable, new RawFundPriceData(List.of()));
    mutable.add("FBAU");
    assertThat(result.fundCodes()).containsExactly("FAEU");
  }
}
