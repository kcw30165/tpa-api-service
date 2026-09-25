package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceRangeCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import reactor.core.publisher.Mono;

/**
 * Outbound seam for the TRPGetFundPrice operation.
 *
 * <p>{@code ApimFundPriceSourceAdapter} is the sole implementation. Application-layer use cases
 * must depend on this port, never on the concrete adapter directly.
 *
 * <p>Two methods, deliberately: {@code fetchPrice} serves the QUERY path (a single FE-validated
 * date, mode-dependent from-date/to-date expansion inside the adapter); {@code fetchPriceRange}
 * serves the EXPORT path (an explicit from-date/to-date range with no mode concept at all,
 * confirmed design). This interface can no longer be {@code @FunctionalInterface} as a direct,
 * deliberate consequence -- flagged explicitly, not an accidental side effect.
 */
public interface FundPriceSourcePort {
  Mono<RawFundPriceData> fetchPrice(FetchFundPriceCommand command);

  Mono<RawFundPriceData> fetchPriceRange(FetchFundPriceRangeCommand command);
}
