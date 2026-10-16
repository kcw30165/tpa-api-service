package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonOptionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonOptionsResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonTimeRangeParser;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData.RawFundComparisonOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Maps {@link RawFundComparisonOptionsData} to the public {@link FundComparisonOptionsResponse},
 * per docs/design/13-fund-comparison/02-api-contract.md sections 2.2 and 2.4.
 *
 * <p>Fund-name selection re-expresses {@code FundPriceWebMapper.displayFundName}'s exact rule
 * (zh_HK uses the Chinese name when non-blank, else the English name; any other language always
 * uses the English name) against {@link RawFundComparisonOption} -- an accepted, intentional small
 * duplication, since there is no shared type between that record and {@code FundPriceRowResult}.
 *
 * <p>Sorting is a dedicated small comparator (case-insensitive display-name ascending, then fund
 * code) -- NOT {@code FundPriceSortingSupport} reused directly, since that class's risk-level and
 * bid-price tie-breakers have no equivalent in options data.
 *
 * <p>Time-range normalization delegates entirely to {@link FundComparisonTimeRangeParser}
 * (Milestone 1); this mapper does not reimplement grammar validation, deduplication, or
 * default-selection, and propagates {@code FundComparisonRetrievalException} naturally when no
 * valid token remains.
 */
@Component
public class FundComparisonOptionsMapper {

  public FundComparisonOptionsResponse toResponse(RawFundComparisonOptionsData raw, String lang) {
    Objects.requireNonNull(raw, "raw must not be null");

    var timeRangeResult = FundComparisonTimeRangeParser.parse(raw.rawTimeRange());

    Comparator<RawFundComparisonOption> order =
        Comparator.comparing(
                (RawFundComparisonOption option) -> caseless(displayFundName(option, lang)))
            .thenComparing(RawFundComparisonOption::fundCode);

    List<FundComparisonOptionResponse> funds =
        raw.funds().stream()
            .sorted(order)
            .map(
                option ->
                    new FundComparisonOptionResponse(
                        option.fundCode(), displayFundName(option, lang)))
            .toList();

    return new FundComparisonOptionsResponse(
        timeRangeResult.timeRanges(), timeRangeResult.defaultTimeRange(), funds);
  }

  private static String displayFundName(RawFundComparisonOption option, String lang) {
    if (RequestLanguageResolver.isZhHk(lang)) {
      return StringUtils.hasText(option.chinFundName()) ? option.chinFundName() : option.fundName();
    }
    return option.fundName();
  }

  private static String caseless(String value) {
    return value.toLowerCase(Locale.ROOT);
  }
}
