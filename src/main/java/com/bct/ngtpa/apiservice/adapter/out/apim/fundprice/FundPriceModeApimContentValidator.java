package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceModeApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceModeData;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Validates and normalizes TRPGetFundPriceMode's downstream content.
 *
 * <p>Every invalid-downstream-contract case here fails closed with the single generic {@link
 * FundPriceRetrievalException}, per fund-price-error-matrix.md's session/contract error section.
 */
final class FundPriceModeApimContentValidator {

  private static final List<String> SUPPORTED_MODES = List.of("DAILY", "MONTHLY");
  private static final List<String> SUPPORTED_FUND_PRICE_TYPES =
      List.of("BIDOFFER PRICE", "UNIT PRICE");

  private FundPriceModeApimContentValidator() {}

  static TrpGetFundPriceModeApimDataItem requireSingleItem(
      List<TrpGetFundPriceModeApimDataItem> data) {
    if (data == null || data.size() != 1 || data.get(0) == null) {
      throw FundPriceRetrievalException.systemFailure();
    }
    return data.get(0);
  }

  static RawFundPriceModeData normalize(
      TrpGetFundPriceModeApimDataItem item, DateTimeFormatter formatter) {
    String mode = item.mode();
    if (mode == null || mode.isBlank() || !SUPPORTED_MODES.contains(mode)) {
      throw FundPriceRetrievalException.systemFailure();
    }
    String fundPriceType = item.fundPriceType();
    if (fundPriceType == null
        || fundPriceType.isBlank()
        || !SUPPORTED_FUND_PRICE_TYPES.contains(fundPriceType)) {
      throw FundPriceRetrievalException.systemFailure();
    }
    if (item.maximumFundPriceHistoryMonths() <= 0) {
      throw FundPriceRetrievalException.systemFailure();
    }
    var rawDates = item.availableFundPriceDates();
    if (rawDates == null || rawDates.isEmpty()) {
      throw FundPriceRetrievalException.systemFailure();
    }
    List<LocalDate> parsed =
        rawDates.stream().map(raw -> parseStrict(raw, formatter)).toList();
    return new RawFundPriceModeData(
        mode, fundPriceType, parsed, item.maximumFundPriceHistoryMonths());
  }

  private static LocalDate parseStrict(String raw, DateTimeFormatter formatter) {
    if (raw == null || raw.isBlank()) {
      throw FundPriceRetrievalException.systemFailure();
    }
    try {
      return LocalDate.parse(raw.trim(), formatter);
    } catch (DateTimeParseException exception) {
      throw FundPriceRetrievalException.systemFailure();
    }
  }
}
