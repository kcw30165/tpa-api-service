package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceModeCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceModeData;
import reactor.core.publisher.Mono;

/**
 * Outbound seam for the TRPGetFundPriceMode operation, mirroring {@link AccountBalanceSourcePort}'s
 * exact shape: a single fetch method taking the command and returning the
 * already-validated/normalized result.
 *
 * <p>{@code ApimFundPriceModeSourceAdapter} is the sole implementation. Application-layer use cases
 * must depend on this port, never on the concrete adapter directly.
 */
@FunctionalInterface
public interface FundPriceModeSourcePort {
  Mono<RawFundPriceModeData> fetchMode(FetchFundPriceModeCommand command);
}
