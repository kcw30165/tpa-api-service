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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates the inbound {@code POST /api/v1/fund-comparison/prices} request and maps the resolved
 * {@link FundComparisonPricesResult} to the public {@link FundComparisonPricesResponse}, per
 * docs/design/13-fund-comparison/04-validation-and-error-handling.md section 4.3 and
 * 02-api-contract.md section 2.3.
 *
 * <p>Normalization (09 section 9.5.4, sub-task 5.4): fund codes are trimmed and de-duplicated
 * case-insensitively, keeping the first spelling; the time range is trimmed and its syntax is
 * checked in upper case, so {@code 10d} passes. Membership against the offered options, and the
 * switch to the offered spelling, happen later in {@code FundComparisonSelectedPricesUseCase}.
 *
 * <p>The grammar check here ({@link #VALID_TIME_RANGE_TOKEN}) intentionally duplicates {@code
 * FundComparisonDateRangeCalculator}'s pattern rather than reusing {@code
 * FundComparisonTimeRangeParser}: a malformed request token is a public request-validation failure
 * ({@link FundComparisonRequestInvalidException}), not a retrieval failure.
 *
 * <p>Series construction: a fund code with zero matching price rows is omitted from the series
 * entirely, never included with an empty {@code prices} array (section 4.5).
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
    Map<String, String> codesByKey = new LinkedHashMap<>();
    for (String code : request.fundCodes()) {
      if (code == null || code.isBlank()) {
        throw new FundComparisonRequestInvalidException("fundCodes must not contain a blank entry");
      }
      String trimmed = code.trim();
      codesByKey.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }
    if (codesByKey.isEmpty()) {
      throw new FundComparisonRequestInvalidException(
          "fundCodes must contain at least one fund code");
    }
    if (codesByKey.size() > MAX_FUND_CODES) {
      throw new FundComparisonRequestInvalidException(
          "fundCodes must not contain more than " + MAX_FUND_CODES + " unique fund codes");
    }
    String timeRange = request.timeRange() == null ? null : request.timeRange().trim();
    if (timeRange == null
        || !VALID_TIME_RANGE_TOKEN.matcher(timeRange.toUpperCase(Locale.ROOT)).matches()) {
      throw new FundComparisonRequestInvalidException(
          "timeRange must be a valid XD/XM/XY/YTD token");
    }
    return new FundComparisonPricesValidatedRequest(List.copyOf(codesByKey.values()), timeRange);
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
