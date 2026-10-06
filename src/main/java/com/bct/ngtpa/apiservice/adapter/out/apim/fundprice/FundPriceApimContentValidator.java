package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimDataItem.FundInfoEntry;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimDataItem.FundPriceEntry;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceRow;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reconciles and validates TRPGetFundPrice's downstream content.
 *
 * <p>Structurally SEPARATE from {@link FundPriceModeApimContentValidator}: this validator joins
 * two arrays (FundPrice x FundInfo by fund-code) rather than normalizing one flat item, and its
 * supported risk-level value set is validated here (unlike max-risk-level on the mode side, which
 * is deliberately never validated). No shared base class -- the two operations validate genuinely
 * different shapes.
 *
 * <p>Reconciliation direction (confirmed): iterates FundPrice rows and looks each up by fund-code
 * in FundInfo. A FundPrice row with no matching FundInfo fails closed (the REQUIRED direction per
 * fund-price-apim-contract.md's invariant "every FundPrice record resolves to exactly one FundInfo
 * record"). A FundInfo entry with no matching FundPrice row is never visited by this iteration
 * order -- it is skipped and logged at WARN, not an error.
 *
 * <p>{@code price-date} parses as ISO ({@code yyyy-MM-dd}), confirmed from the doc's own
 * byte-exact response sample -- a fixed formatter, not the configurable dd/MM/yyyy full-date
 * pattern used elsewhere in this design pack.
 */
final class FundPriceApimContentValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(FundPriceApimContentValidator.class);

  private static final List<String> SUPPORTED_RISK_LEVELS =
      List.of("0", "0.5", "1", "1.5", "2", "2.5", "3", "3.5", "4", "4.5", "5");

  private FundPriceApimContentValidator() {}

  static RawFundPriceData reconcile(TrpGetFundPriceApimDataItem item) {
    if (item == null) {
      throw FundPriceRetrievalException.systemFailure();
    }
    List<FundPriceEntry> fundPrice = item.fundPrice();
    List<FundInfoEntry> fundInfo = item.fundInfo();
    if (fundPrice == null || fundPrice.isEmpty() || fundInfo == null || fundInfo.isEmpty()) {
      throw FundPriceRetrievalException.systemFailure();
    }

    Map<String, FundInfoEntry> infoByFundCode = new LinkedHashMap<>();
    for (FundInfoEntry entry : fundInfo) {
      // A null entry is already caught by requiredFundCode(...) below (entry == null ?
      // null : ...) evaluates to requiredFundCode(null), which fails the null/blank check
      // and throws immediately -- so entry is guaranteed non-null past this line. A
      // redundant "entry == null ||" on the next check was removed here as unreachable
      // dead code (coverage-closure script 095), rather than left permanently uncovered.
      String fundCode = requiredFundCode(entry == null ? null : entry.fundCode());
      if (entry.fundName() == null || entry.fundName().isBlank()) {
        throw FundPriceRetrievalException.systemFailure();
      }
      validateRiskLevel(entry.riskLevel());
      if (infoByFundCode.putIfAbsent(fundCode, entry) != null) {
        // Duplicate fund-code within FundInfo is an invalid downstream contract.
        throw FundPriceRetrievalException.systemFailure();
      }
    }

    Map<String, Boolean> seenFundCodePriceDatePairs = new LinkedHashMap<>();
    List<RawFundPriceRow> rows = new java.util.ArrayList<>();
    for (FundPriceEntry priceEntry : fundPrice) {
      if (priceEntry == null) {
        throw FundPriceRetrievalException.systemFailure();
      }
      String fundCode = requiredFundCode(priceEntry.fundCode());
      LocalDate priceDate = parseIsoDate(priceEntry.priceDate());
      String pairKey = fundCode + "|" + priceDate;
      if (seenFundCodePriceDatePairs.putIfAbsent(pairKey, Boolean.TRUE) != null) {
        // Duplicate (fund-code, price-date) pair is an invalid downstream contract.
        throw FundPriceRetrievalException.systemFailure();
      }
      if (priceEntry.bidPrice() == null || priceEntry.offerPrice() == null) {
        throw FundPriceRetrievalException.systemFailure();
      }

      FundInfoEntry matchedInfo = infoByFundCode.get(fundCode);
      if (matchedInfo == null) {
        // A FundPrice row with no matching FundInfo record is the REQUIRED direction: fails
        // closed, per the doc's invariant.
        throw FundPriceRetrievalException.systemFailure();
      }

      rows.add(
          new RawFundPriceRow(
              fundCode,
              matchedInfo.fundName(),
              matchedInfo.chineseFundName(),
              priceDate,
              matchedInfo.fundCurrency(),
              priceEntry.bidPrice(),
              priceEntry.offerPrice(),
              matchedInfo.riskLevel()));
    }

    // FundInfo entries never visited above (no matching FundPrice row) are skipped, not an
    // error -- logged at WARN for operational visibility only.
    for (String fundCode : infoByFundCode.keySet()) {
      boolean matched = rows.stream().anyMatch(row -> row.fundCode().equals(fundCode));
      if (!matched) {
        LOGGER.warn(
            "event=fund-price.reconciliation.orphan-fund-info fundCode=<redacted> "
                + "reason=no-matching-fund-price-row-in-range");
      }
    }

    return new RawFundPriceData(rows);
  }

  private static String requiredFundCode(String fundCode) {
    if (fundCode == null || fundCode.isBlank()) {
      throw FundPriceRetrievalException.systemFailure();
    }
    return fundCode;
  }

  private static LocalDate parseIsoDate(String raw) {
    if (raw == null || raw.isBlank()) {
      throw FundPriceRetrievalException.systemFailure();
    }
    try {
      return LocalDate.parse(raw.trim());
    } catch (DateTimeParseException exception) {
      throw FundPriceRetrievalException.systemFailure();
    }
  }

  private static void validateRiskLevel(String riskLevel) {
    if (riskLevel == null || riskLevel.isEmpty()) {
      return;
    }
    if (!SUPPORTED_RISK_LEVELS.contains(riskLevel)) {
      throw FundPriceRetrievalException.systemFailure();
    }
  }
}
