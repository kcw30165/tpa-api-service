package com.bct.ngtpa.apiservice.application.dto.fundprice;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Cached Fund Price mode metadata carried on {@link
 * com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession}, so
 * subsequent price and export requests can validate and derive ranges without recalling
 * TRPGetFundPriceMode.
 *
 * <p>Deliberately mirrors {@link RawFundPriceModeData}'s exact 5-field shape (same names, same
 * types) -- this is a straight pass-through cache of a validated mode-fetch result, not an
 * independently-designed shape. {@code mode} and {@code fundPriceType} are required, matching
 * {@code RawFundPriceModeData}'s own null-checks. {@code maxRiskLevel} is deliberately NOT
 * validated or defaulted here, per fund-price-apim-contract.md: {@code null} and blank are both
 * legitimate values.
 */
public record FundPriceModeSessionMetadata(
    String mode,
    String fundPriceType,
    List<LocalDate> availableFundPriceDates,
    int maximumFundPriceHistoryMonths,
    String maxRiskLevel) {

  public FundPriceModeSessionMetadata {
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(fundPriceType, "fundPriceType must not be null");
    availableFundPriceDates =
        availableFundPriceDates == null ? List.of() : List.copyOf(availableFundPriceDates);
  }
}
