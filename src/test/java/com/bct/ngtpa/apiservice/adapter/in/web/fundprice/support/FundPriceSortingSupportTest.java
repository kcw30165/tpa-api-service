package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceQueryResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first tests for FundPriceSortingSupport: rows ordered by risk level ascending (empty ranks
 * highest, sorts last), then English fund name ascending, then unit price ascending (the
 * downstream bid-price under both fund-price-type values), per fund-price-api-contract.md.
 *
 * <p>Uses the existing, proven SortEngine/@ApplySorts framework directly -- this is confirmed the
 * SECOND consumer of that generic mechanism (ContributionSortingSupport being the first).
 */
class FundPriceSortingSupportTest {

  private FundPriceRowResult row(String fundCode, String fundName, String riskLevel, String bidPrice) {
    return new FundPriceRowResult(
        fundCode, fundName, "n", LocalDate.of(2026, 5, 5), "HKD", new BigDecimal(bidPrice), new BigDecimal(bidPrice), riskLevel);
  }

  @Test
  void sortsQueryResultRowsByRiskLevelThenFundNameThenUnitPrice() {
    var unsorted =
        new FundPriceQueryResult(
            List.of(
                row("C", "Zebra Fund", "3", "5.0"),
                row("A", "Alpha Fund", "1", "9.0"),
                row("B", "Beta Fund", "1", "2.0")),
            "BIDOFFER PRICE",
            "3");

    var sorted = new FundPriceSortingSupport().sort(unsorted);

    // CORRECTED (script 118): the confirmed sort rule is risk level -> fund name -> unit price.
    // A and B share risk level "1" -- the tie-break is FUND NAME ascending (Alpha < Beta), not
    // unit price. The original assertion here ("B","A","C") was a bug in script 117's own test.
    assertThat(sorted.rows()).extracting(FundPriceRowResult::fundCode).containsExactly("A", "B", "C");
  }

  @Test
  void blankRiskLevelSortsLastAsHighestRisk() {
    var unsorted =
        new FundPriceQueryResult(
            List.of(
                row("A", "Alpha Fund", "", "1.0"),
                row("B", "Beta Fund", "1", "1.0")),
            "BIDOFFER PRICE",
            null);

    var sorted = new FundPriceSortingSupport().sort(unsorted);

    assertThat(sorted.rows()).extracting(FundPriceRowResult::fundCode).containsExactly("B", "A");
  }

  @Test
  void sortsExportResultRowsIdentically() {
    var unsorted =
        new FundPriceExportResult(
            List.of(
                row("C", "Zebra Fund", "3", "5.0"),
                row("A", "Alpha Fund", "1", "9.0")),
            "UNIT PRICE",
            "3");

    var sorted = new FundPriceSortingSupport().sort(unsorted);

    assertThat(sorted.rows()).extracting(FundPriceRowResult::fundCode).containsExactly("A", "C");
  }
}
