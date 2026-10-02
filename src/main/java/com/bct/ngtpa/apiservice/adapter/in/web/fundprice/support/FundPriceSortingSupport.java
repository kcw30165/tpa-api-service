package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.support;

import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.mapper.FundPriceWebMapper;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceQueryResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRiskLevels;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Web-adapter sorting for Fund Price.
 *
 * <p><b>Query</b> ({@link #sort(FundPriceQueryResult, String)}): risk level ascending, where {@code
 * null}, blank and {@code "0"} mean "no risk level" and sort last as the highest risk; then the
 * fund name the user sees for the request language (zh_HK: Chinese name, falling back to the
 * English name), case-insensitive, in character-code order (English names therefore precede Chinese
 * names); then unit price (the downstream bid price) ascending; ties broken by English name and
 * then fund code so the order is deterministic.
 *
 * <p><b>Export</b> ({@link #sort(FundPriceExportResult)}): price date descending (latest first),
 * then risk level ascending with the same "no risk level last" rule, then English fund name
 * ascending (case-insensitive), then fund code.
 *
 * <p>Uses plain comparators rather than the generic SortEngine, which has no notion of "0" as an
 * unassigned value or of language-dependent display names.
 */
@Component
public class FundPriceSortingSupport {

  private static final Comparator<BigDecimal> RISK_ASC_UNASSIGNED_LAST =
      Comparator.nullsLast(Comparator.naturalOrder());

  public FundPriceQueryResult sort(FundPriceQueryResult result, String lang) {
    Objects.requireNonNull(result, "result must not be null");
    Comparator<FundPriceRowResult> order =
        Comparator.comparing(
                (FundPriceRowResult row) -> FundPriceRiskLevels.sortKey(row.riskLevel()),
                RISK_ASC_UNASSIGNED_LAST)
            .thenComparing(row -> caseless(FundPriceWebMapper.displayFundName(row, lang)))
            .thenComparing(FundPriceRowResult::bidPrice)
            .thenComparing(row -> caseless(row.fundName()))
            .thenComparing(FundPriceRowResult::fundCode);
    return new FundPriceQueryResult(
        result.rows().stream().sorted(order).toList(),
        result.fundPriceType(),
        result.maxRiskLevel());
  }

  public FundPriceExportResult sort(FundPriceExportResult result) {
    Objects.requireNonNull(result, "result must not be null");
    Comparator<FundPriceRowResult> order =
        Comparator.comparing(FundPriceRowResult::priceDate, Comparator.reverseOrder())
            .thenComparing(
                (FundPriceRowResult row) -> FundPriceRiskLevels.sortKey(row.riskLevel()),
                RISK_ASC_UNASSIGNED_LAST)
            .thenComparing(row -> caseless(row.fundName()))
            .thenComparing(FundPriceRowResult::fundCode);
    return new FundPriceExportResult(
        result.rows().stream().sorted(order).toList(),
        result.fundPriceType(),
        result.maxRiskLevel(),
        result.unitPricePrecision(),
        result.mode());
  }

  private static String caseless(String value) {
    return value.toLowerCase(Locale.ROOT);
  }
}
