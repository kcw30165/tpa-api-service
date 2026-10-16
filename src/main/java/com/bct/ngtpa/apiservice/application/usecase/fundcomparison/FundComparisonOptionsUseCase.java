package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper.FundComparisonOptionsMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonOptionsResponse;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonOptionsSourcePort;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Orchestrates the TRPGetFundCmpOpt source port and the options mapper, per
 * docs/design/13-fund-comparison/07-implementation-plan.md section 7.3 sub-task 2.3: no
 * persistence, no caching (confirmed non-goal -- unlike {@code FundPriceModeUseCase}'s
 * session-caching pattern, confirmed by direct investigation not to apply here). Mirrors a
 * simplified {@code GetFundFactSheetService}-style orchestration: calls the port, maps the result
 * through a pure mapper, and defensively wraps any exception that is not already a {@link
 * FundComparisonRetrievalException} into one.
 *
 * <p>Deliberately NOT annotated with a Spring stereotype: the application layer must not depend on
 * Spring Framework (confirmed repo-wide convention, CleanArchitectureDependencyTest). Wired as a
 * bean explicitly from a Spring {@code @Configuration} class (sub-task 2.4).
 */
public final class FundComparisonOptionsUseCase {

  private final FundComparisonOptionsSourcePort sourcePort;
  private final FundComparisonOptionsMapper mapper;

  public FundComparisonOptionsUseCase(
      FundComparisonOptionsSourcePort sourcePort, FundComparisonOptionsMapper mapper) {
    this.sourcePort = Objects.requireNonNull(sourcePort, "sourcePort must not be null");
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
  }

  public Mono<FundComparisonOptionsResponse> getOptions(
      FetchFundComparisonOptionsCommand command, String lang) {
    Objects.requireNonNull(command, "command must not be null");

    return sourcePort
        .fetchOptions(command)
        .map(raw -> mapper.toResponse(raw, lang))
        .onErrorMap(
            exception ->
                exception instanceof FundComparisonRetrievalException
                    ? exception
                    : FundComparisonRetrievalException.systemFailure());
  }
}
