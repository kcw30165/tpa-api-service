package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates and normalizes a raw downstream {@code time-range} string per
 * docs/design/13-fund-comparison/04-validation-and-error-handling.md section 4.2.
 *
 * <p>A valid token matches {@code ^(?:[1-9][0-9]*[DMY]|YTD)$} (XD, XM, XY, or YTD, where X is a
 * positive integer). Invalid tokens are dropped with logging, but only when at least one valid
 * token remains; if none remain, this fails with {@link FundComparisonRetrievalException}, since a
 * missing/blank/wholly-invalid time-range is a retrieval failure, not an empty success. Duplicates
 * are removed retaining first occurrence; normalized downstream order is preserved. The default
 * token is selected using fixed unit priority D &lt; M &lt; Y &lt; YTD, with the smallest integer
 * chosen within D/M/Y; selection is independent of array order.
 */
public final class FundComparisonTimeRangeParser {

  private static final Logger LOG = LoggerFactory.getLogger(FundComparisonTimeRangeParser.class);
  private static final Pattern VALID_TOKEN = Pattern.compile("^(?:[1-9]\\d*[DMY]|YTD)$");

  private FundComparisonTimeRangeParser() {}

  public static FundComparisonTimeRangeParseResult parse(String rawTimeRange) {
    if (rawTimeRange == null || rawTimeRange.isBlank()) {
      LOG.warn("Fund Comparison options time-range is missing or blank");
      throw FundComparisonRetrievalException.systemFailure();
    }

    var validTokens = new LinkedHashSet<String>();
    for (String rawToken : rawTimeRange.split(",", -1)) {
      String token = rawToken.trim();
      if (!isUsableToken(token)) {
        continue;
      }
      if (!validTokens.add(token)) {
        LOG.warn("Dropping duplicate Fund Comparison time-range token: {}", token);
      }
    }

    if (validTokens.isEmpty()) {
      LOG.warn("No valid Fund Comparison time-range token remained after normalization");
      throw FundComparisonRetrievalException.systemFailure();
    }

    List<String> orderedTokens = new ArrayList<>(validTokens);
    String defaultToken = selectDefault(orderedTokens);
    return new FundComparisonTimeRangeParseResult(orderedTokens, defaultToken);
  }

  private static boolean isUsableToken(String token) {
    if (token.isEmpty()) {
      return false;
    }
    if (!VALID_TOKEN.matcher(token).matches()) {
      LOG.warn("Dropping invalid Fund Comparison time-range token: {}", token);
      return false;
    }
    return true;
  }

  private static String selectDefault(List<String> tokens) {
    String bestToken = null;
    int bestUnitPriority = Integer.MAX_VALUE;
    int bestValue = Integer.MAX_VALUE;

    for (String token : tokens) {
      int unitPriority;
      int value;
      if ("YTD".equals(token)) {
        unitPriority = 3;
        value = 0;
      } else {
        char unit = token.charAt(token.length() - 1);
        value = Integer.parseInt(token.substring(0, token.length() - 1));
        unitPriority =
            switch (unit) {
              case 'D' -> 0;
              case 'M' -> 1;
              case 'Y' -> 2;
              default -> throw new IllegalStateException("Unreachable: token already validated");
            };
      }

      if (unitPriority < bestUnitPriority
          || (unitPriority == bestUnitPriority && value < bestValue)) {
        bestUnitPriority = unitPriority;
        bestValue = value;
        bestToken = token;
      }
    }

    return bestToken;
  }
}
