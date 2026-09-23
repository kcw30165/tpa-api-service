package com.bct.ngtpa.apiservice.application.dto.fundprice;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Normalized result of the TRPGetFundPriceMode operation, ready for authenticated-session
 * caching and eventual mapping to the public mode response.
 *
 * <p>{@code mode} is either {@code "DAILY"} or {@code "MONTHLY"} (contract: "Java has approved
 * interpretation only for DAILY and MONTHLY... must not invent WEEKLY behavior"). {@code
 * fundPriceType} is either {@code "BIDOFFER PRICE"} or {@code "UNIT PRICE"}.
 */
public record RawFundPriceModeData(
    String mode,
    String fundPriceType,
    List<LocalDate> availableFundPriceDates,
    int maximumFundPriceHistoryMonths) {

  public RawFundPriceModeData {
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(fundPriceType, "fundPriceType must not be null");
    availableFundPriceDates =
        availableFundPriceDates == null ? List.of() : List.copyOf(availableFundPriceDates);
  }
}
