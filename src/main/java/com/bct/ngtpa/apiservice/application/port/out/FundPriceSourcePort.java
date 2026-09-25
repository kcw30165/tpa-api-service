package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import reactor.core.publisher.Mono;

/**
 * Outbound seam for the TRPGetFundPrice operation, mirroring {@link FundPriceModeSourcePort}'s
 * exact shape: a single fetch method taking the command and returning the already-reconciled
 * result.
 *
 * <p>{@code ApimFundPriceSourceAdapter} is the sole implementation. Application-layer use cases
 * must depend on this port, never on the concrete adapter directly.
 */
@FunctionalInterface
public interface FundPriceSourcePort {
  Mono<RawFundPriceData> fetchPrice(FetchFundPriceCommand command);
}
