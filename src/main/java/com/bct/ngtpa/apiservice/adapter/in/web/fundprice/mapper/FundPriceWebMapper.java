package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response.FundPriceQueryResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response.FundPriceRowResponse;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceQueryResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceRowResult;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Bid/offer-vs-unit field selection is driven by the cached {@code fundPriceType}; priceDate is
 * formatted via the shared {@code fundPriceFullDateFormatter} bean (dd/MM/yyyy, per the API
 * contract's own "05/05/2026" example -- NOT ISO), the same formatter type
 * {@code ApimFundPriceSourceAdapter} already depends on.
 *
 * <p>KNOWN GAP, flagged explicitly, not silently worked around: {@link FundPriceRowResult} does
 * not carry a Chinese fund name forward from {@code RawFundPriceRow} (which does have one) --
 * this mapper can only ever emit the English {@code fundName}, so the API contract's
 * "fundName resolves per Accept-Language" rule is NOT yet fully implementable. Fixing this
 * requires a change to the already-committed {@code FundPriceQueryUseCase}/{@code
 * FundPriceRowResult}, deliberately deferred to its own follow-up stage rather than made
 * unilaterally here.
 */
@Component
public class FundPriceWebMapper {

  private static final String BIDOFFER_PRICE_TYPE = "BIDOFFER PRICE";

  private final DateTimeFormatter formatter;

  public FundPriceWebMapper(DateTimeFormatter fundPriceFullDateFormatter) {
    this.formatter =
        Objects.requireNonNull(fundPriceFullDateFormatter, "formatter must not be null");
  }

  public FundPriceQueryResponse toQueryResponse(FundPriceQueryResult result) {
    return new FundPriceQueryResponse(
        mapRows(result.rows(), result.fundPriceType()), result.maxRiskLevel());
  }

  public List<FundPriceRowResponse> toExportRows(FundPriceExportResult result) {
    return mapRows(result.rows(), result.fundPriceType());
  }

  private List<FundPriceRowResponse> mapRows(List<FundPriceRowResult> rows, String fundPriceType) {
    boolean bidOffer = BIDOFFER_PRICE_TYPE.equals(fundPriceType);
    return rows.stream().map(row -> toRowResponse(row, bidOffer)).toList();
  }

  private FundPriceRowResponse toRowResponse(FundPriceRowResult row, boolean bidOffer) {
    return new FundPriceRowResponse(
        row.fundCode(),
        row.fundName(),
        row.priceDate().format(formatter),
        row.fundCurrency(),
        bidOffer ? row.bidPrice() : null,
        bidOffer ? row.offerPrice() : null,
        bidOffer ? null : row.bidPrice(),
        row.riskLevel());
  }
}
