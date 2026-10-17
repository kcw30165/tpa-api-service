package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.request.FundComparisonPricesRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonPricePointResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonPricesResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonSeriesResponse;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonPricesResult;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonPricesValidatedRequest;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceRow;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRequestInvalidException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates the inbound {@code POST /api/v1/fund-comparison/prices} request and maps the resolved
 * {@link FundComparisonPricesResult} to the public {@link FundComparisonPricesResponse}, per
 * docs/design/13-fund-comparison/04-validation-and-error-handling.md section 4.3 and
 * 02-api-contract.md section 2.3 (Milestone 3, sub-task 3.2).
 *
 * <p>The time-range grammar check here ({@link #VALID_TIME_RANGE_TOKEN}) intentionally duplicates
 * {@code FundComparisonDateRangeCalculator}'s own internal pattern rather than reusing {@code
 * FundComparisonTimeRangeParser}: that parser normalizes a comma-separated downstream options
 * string and throws {@code FundComparisonRetrievalException} on failure, whereas a single malformed
 * request-level token here is a public request-validation failure ({@link
 * FundComparisonRequestInvalidException}), per section 4.3. This mirrors {@code
 * FundComparisonOptionsMapper}'s own documented precedent of small, intentional cross-layer
 * duplication where two call sites have genuinely different exception semantics.
 *
 * <p>Series construction (section 4.5's "legitimate empty price result" and section 2.4's "group
 * rows by fund-code into series, ordered by request's first-occurrence order"): a fund code with
 * zero matching price rows is OMITTED from the series entirely, never included with an empty {@code
 * prices} array -- confirmed by section 4.5's own example, where a wholly empty downstream result
 * maps to {@code series: []} (zero entries), not one empty entry per requested fund.
 */
@Component
public class FundComparisonPricesWebMapper {

  private static final Pattern VALID_TIME_RANGE_TOKEN = Pattern.compile("^(?:[1-9]\\d*[DMY]|YTD)$");
  private static final int MAX_FUND_CODES = 5;

  private final DateTimeFormatter publicDateFormatter;

  public FundComparisonPricesWebMapper(DateTimeFormatter fundPriceFullDateFormatter) {
    this.publicDateFormatter =
        Objects.requireNonNull(
            fundPriceFullDateFormatter, "fundPriceFullDateFormatter must not be null");
  }

  public FundComparisonPricesValidatedRequest validate(FundComparisonPricesRequest request) {
    if (request == null) {
      throw new FundComparisonRequestInvalidException("request body must not be null");
    }
    var dedupedCodes = new LinkedHashSet<String>();
    for (String code : request.fundCodes()) {
      if (code == null || code.isBlank()) {
        throw new FundComparisonRequestInvalidException("fundCodes must not contain a blank entry");
      }
      dedupedCodes.add(code);
    }
    if (dedupedCodes.isEmpty()) {
      throw new FundComparisonRequestInvalidException(
          "fundCodes must contain at least one fund code");
    }
    if (dedupedCodes.size() > MAX_FUND_CODES) {
      throw new FundComparisonRequestInvalidException(
          "fundCodes must not contain more than " + MAX_FUND_CODES + " unique fund codes");
    }
    String timeRange = request.timeRange();
    if (timeRange == null || !VALID_TIME_RANGE_TOKEN.matcher(timeRange).matches()) {
      throw new FundComparisonRequestInvalidException(
          "timeRange must be a valid XD/XM/XY/YTD token");
    }
    return new FundComparisonPricesValidatedRequest(List.copyOf(dedupedCodes), timeRange);
  }

  public FundComparisonPricesResponse toResponse(FundComparisonPricesResult result) {
    Objects.requireNonNull(result, "result must not be null");
    Map<String, List<RawFundPriceRow>> rowsByFundCode = new LinkedHashMap<>();
    for (RawFundPriceRow row : result.rawData().rows()) {
      rowsByFundCode.computeIfAbsent(row.fundCode(), key -> new ArrayList<>()).add(row);
    }
    List<FundComparisonSeriesResponse> series = new ArrayList<>();
    for (String fundCode : result.fundCodes()) {
      List<RawFundPriceRow> rows = rowsByFundCode.get(fundCode);
      if (rows == null || rows.isEmpty()) {
        continue;
      }
      List<FundComparisonPricePointResponse> prices =
          rows.stream()
              .sorted(Comparator.comparing(RawFundPriceRow::priceDate))
              .map(
                  row ->
                      new FundComparisonPricePointResponse(
                          row.priceDate().format(publicDateFormatter), row.bidPrice()))
              .toList();
      series.add(new FundComparisonSeriesResponse(fundCode, prices));
    }
    return new FundComparisonPricesResponse(
        result.fromDate().format(publicDateFormatter),
        result.toDate().format(publicDateFormatter),
        series);
  }
}
