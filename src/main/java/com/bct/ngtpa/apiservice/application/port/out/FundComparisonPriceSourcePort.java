package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import reactor.core.publisher.Mono;

/**
 * Outbound seam for Fund Comparison's reuse of the TRPGetFundPrice operation, per
 * docs/design/13-fund-comparison/07-implementation-plan.md section 7.4 (corrected sub-task 3.1). A
 * single, minimal method -- Fund Comparison's prices use case depends ONLY on this port, never on
 * {@link FundPriceSourcePort} (which it never calls), and {@code FundPriceSourcePort} is
 * correspondingly never widened with a method Fund Price's own use case never calls. This avoids an
 * Interface Segregation Principle violation a single combined port would introduce.
 *
 * <p>{@code ApimFundPriceSourceAdapter} (the EXISTING, already-tested Fund Price adapter) is the
 * sole implementation -- additively extended to implement this interface alongside {@link
 * FundPriceSourcePort}, reusing the exact same downstream wire integration (same request/response
 * DTOs, same {@code FundPriceApimContentValidator} reconciliation, same error mapping) rather than
 * duplicating it in a separate adapter class.
 */
@FunctionalInterface
public interface FundComparisonPriceSourcePort {
  Mono<RawFundPriceData> fetchPrices(FetchFundComparisonPricesCommand command);
}
