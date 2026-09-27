package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.support;

import java.math.BigInteger;

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
  void sortsExportResultRowsByDateDescendingThenRiskAscendingThenNameAscending() {
    // CORRECTED (script 139, fixing an ordering bug in script 138's own site-patch sequence):
    // export's sort order is DISTINCT from query mode's, per
    // fund-price-excel-export-contract.md's "Rows and sorting" section: price date descending,
    // then risk level ascending (blank ranks last), then English fund name ascending.
    var unsorted =
        new FundPriceExportResult(
            List.of(
                rowWithDate("A", "Zebra Fund", "1", "2026-05-04"),
                rowWithDate("B", "Alpha Fund", "1", "2026-05-05"),
                rowWithDate("C", "Beta Fund", "3", "2026-05-05")),
            "UNIT PRICE",
            "3",
            BigInteger.TWO);

    var sorted = new FundPriceSortingSupport().sort(unsorted);

    assertThat(sorted.rows())
        .extracting(FundPriceRowResult::fundCode)
        .containsExactly("B", "C", "A");
  }

  private FundPriceRowResult rowWithDate(
      String fundCode, String fundName, String riskLevel, String isoDate) {
    return new FundPriceRowResult(
        fundCode,
        fundName,
        "n",
        LocalDate.parse(isoDate),
        "HKD",
        new BigDecimal("1.0"),
        new BigDecimal("1.0"),
        riskLevel);
  }
}
