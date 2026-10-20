package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import reactor.core.publisher.Mono;

/**
 * Outbound seam for the TRPGetFundCmpOpt operation, mirroring {@link FundPriceModeSourcePort}'s
 * exact shape: a single fetch method taking the command and returning the
 * already-structurally-validated raw result.
 *
 * <p>{@code ApimFundComparisonOptionsSourceAdapter} is the sole implementation. Application-layer
 * use cases must depend on this port, never on the concrete adapter directly. This port is fully
 * separate from {@code FundPriceSourcePort} and {@code FundPriceModeSourcePort} -- confirmed, this
 * feature introduces its own dedicated port rather than extending an existing one.
 */
@FunctionalInterface
public interface FundComparisonOptionsSourcePort {
  Mono<RawFundComparisonOptionsData> fetchOptions(FetchFundComparisonOptionsCommand command);
}
