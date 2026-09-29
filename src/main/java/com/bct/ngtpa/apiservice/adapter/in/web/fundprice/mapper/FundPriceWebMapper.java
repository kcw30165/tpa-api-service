package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response.FundPriceQueryResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response.FundPriceRowResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceQueryResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response.FundPriceModeResponse;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;
import java.time.LocalDate;

/**
 * Bid/offer-vs-unit field selection is driven by the cached {@code fundPriceType}; priceDate is
 * formatted via the shared {@code fundPriceFullDateFormatter} bean (dd/MM/yyyy, per the API
 * contract's own "05/05/2026" example -- NOT ISO), the same formatter type
 * {@code ApimFundPriceSourceAdapter} already depends on.
 *
 * <p>fundName resolution follows {@code RequestLanguageResolver.isZhHk(lang)}, mirroring {@code
 * ContributionSummaryWebMapper.resolveLabel}'s exact fallback-safe pattern (confirmed, script
 * 120's investigation): zh_HK requests use {@code chineseFundName} when populated, falling back to
 * the English {@code fundName} otherwise; any other language always uses the English name.
 *
 * <p>{@code toExportRows(...)} deliberately KEEPS its existing single-argument signature -- export
 * has no controller endpoint yet (confirmed, {@code FundPriceController}'s own Javadoc), so its own
 * language-selection design is a separate, deferred question. It defaults to English internally
 * rather than guessing an export-specific language rule.
 */
@Component
public class FundPriceWebMapper {

  private static final String BIDOFFER_PRICE_TYPE = "BIDOFFER PRICE";
  private static final String DEFAULT_LANGUAGE = "en";

  private final DateTimeFormatter formatter;

  public FundPriceWebMapper(DateTimeFormatter fundPriceFullDateFormatter) {
    this.formatter = Objects.requireNonNull(fundPriceFullDateFormatter, "formatter must not be null");
  }

  public FundPriceModeResponse toModeResponse(
      FundPriceModeSessionMetadata metadata, LocalDate referenceDate) {
    return new FundPriceModeResponse(
        metadata.mode(),
        metadata.availableFundPriceDates().stream().map(formatter::format).toList(),
        metadata.maximumFundPriceHistoryMonths(),
        referenceDate.format(formatter));
  }

  public FundPriceQueryResponse toQueryResponse(FundPriceQueryResult result, String lang) {
    return new FundPriceQueryResponse(
        mapRows(result.rows(), result.fundPriceType(), lang), result.maxRiskLevel());
  }

  public List<FundPriceRowResponse> toExportRows(FundPriceExportResult result) {
    return mapRows(result.rows(), result.fundPriceType(), DEFAULT_LANGUAGE);
  }

  private List<FundPriceRowResponse> mapRows(
      List<FundPriceRowResult> rows, String fundPriceType, String lang) {
    boolean bidOffer = BIDOFFER_PRICE_TYPE.equals(fundPriceType);
    return rows.stream().map(row -> toRowResponse(row, bidOffer, lang)).toList();
  }

  private FundPriceRowResponse toRowResponse(FundPriceRowResult row, boolean bidOffer, String lang) {
    return new FundPriceRowResponse(
        row.fundCode(),
        resolveFundName(row, lang),
        row.priceDate().format(formatter),
        row.fundCurrency(),
        bidOffer ? row.bidPrice() : null,
        bidOffer ? row.offerPrice() : null,
        bidOffer ? null : row.bidPrice(),
        row.riskLevel());
  }

  /**
   * Fund name shown to the user: zh_HK uses the Chinese name when present, otherwise the English
   * name; any other language uses the English name. Shared with FundPriceSortingSupport so the
   * query sort orders by exactly the name the user sees.
   */
  public static String displayFundName(FundPriceRowResult row, String lang) {
    if (RequestLanguageResolver.isZhHk(lang)) {
      return StringUtils.hasText(row.chineseFundName()) ? row.chineseFundName() : row.fundName();
    }
    return row.fundName();
  }

  private String resolveFundName(FundPriceRowResult row, String lang) {
    return displayFundName(row, lang);
  }
}
