package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonOptionsSourcePort;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Orchestrates the TRPGetFundCmpOpt source port, per
 * docs/design/13-fund-comparison/07-implementation-plan.md section 7.3 sub-task 2.3: no
 * persistence, no caching (confirmed non-goal -- unlike {@code FundPriceModeUseCase}'s
 * session-caching pattern, confirmed by direct investigation not to apply here).
 *
 * <p>Corrected (script 047, closing a Clean Architecture violation confirmed by
 * CleanArchitectureDependencyTest and two other architecture guards): this use case returns the
 * raw, application-layer {@link RawFundComparisonOptionsData} directly -- it no longer depends on
 * {@code FundComparisonOptionsMapper} or {@code FundComparisonOptionsResponse}, both of which live
 * in the adapter.in.web (presentation) layer. Mapping to the public response shape is the
 * CONTROLLER's responsibility, mirroring {@code FundFactSheetController}/{@code
 * GetFundFactSheetService}'s exact confirmed precedent (script 042 evidence): the use case returns
 * an application-layer result, and only the controller calls the web mapper.
 *
 * <p>Deliberately NOT annotated with a Spring stereotype: the application layer must not depend on
 * Spring Framework (confirmed repo-wide convention, CleanArchitectureDependencyTest). Wired as a
 * bean explicitly from a Spring {@code @Configuration} class (sub-task 2.4).
 */
public final class FundComparisonOptionsUseCase {

  private final FundComparisonOptionsSourcePort sourcePort;

  public FundComparisonOptionsUseCase(FundComparisonOptionsSourcePort sourcePort) {
    this.sourcePort = Objects.requireNonNull(sourcePort, "sourcePort must not be null");
  }

  public Mono<RawFundComparisonOptionsData> getOptions(FetchFundComparisonOptionsCommand command) {
    Objects.requireNonNull(command, "command must not be null");

    return sourcePort
        .fetchOptions(command)
        .onErrorMap(
            exception ->
                exception instanceof FundComparisonRetrievalException
                    ? exception
                    : FundComparisonRetrievalException.systemFailure());
  }
}
