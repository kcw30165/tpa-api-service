package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesUseCaseCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonDateRange;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonDateRangeCalculator;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonPricesResult;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Orchestrates the Milestone 3 prices flow (sub-task 3.3), per
 * docs/design/13-fund-comparison/07-implementation-plan.md section 7.4 and 01-solution-design.md
 * section 1.3 ("Prices flow"): resolves the reference date via {@link
 * ReferenceDatePort#resolveReferenceDate(String)}, computes the inclusive date window via {@link
 * FundComparisonDateRangeCalculator} (Milestone 1), joins the already-validated fund codes with
 * commas and no spaces, and calls {@link FundComparisonPriceSourcePort#fetchPrices}.
 *
 * <p>Deliberately NOT annotated with a Spring stereotype (application layer must not depend on
 * Spring Framework, confirmed repo-wide convention): wired as a bean explicitly from a Spring
 * {@code @Configuration} class in sub-task 3.4, mirroring {@code FundComparisonOptionsUseCase}'s
 * own precedent.
 *
 * <p>Error wrapping is scoped to the two downstream-dependent {@link Mono}s (reference-date
 * resolution and the price fetch) -- a {@link FundComparisonDateRangeCalculator} precondition
 * violation (a malformed time-range token reaching this class, which should never happen since
 * {@code FundComparisonPricesWebMapper#validate} already validated the same grammar) is
 * deliberately left unwrapped, per that calculator's own documented intent that such a violation is
 * a programming-error precondition failure, not a public retrieval-unavailable contract.
 */
public final class FundComparisonPricesUseCase {

  private final FundComparisonPriceSourcePort sourcePort;
  private final ReferenceDatePort referenceDatePort;

  public FundComparisonPricesUseCase(
      FundComparisonPriceSourcePort sourcePort, ReferenceDatePort referenceDatePort) {
    this.sourcePort = Objects.requireNonNull(sourcePort, "sourcePort must not be null");
    this.referenceDatePort =
        Objects.requireNonNull(referenceDatePort, "referenceDatePort must not be null");
  }

  public Mono<FundComparisonPricesResult> getPrices(
      FetchFundComparisonPricesUseCaseCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return referenceDatePort
        .resolveReferenceDate(command.accountEnv())
        .onErrorMap(FundComparisonPricesUseCase::toRetrievalException)
        .map(
            referenceDate ->
                FundComparisonDateRangeCalculator.calculate(referenceDate, command.timeRange()))
        .flatMap(dateRange -> fetchAndAssemble(command, dateRange));
  }

  private Mono<FundComparisonPricesResult> fetchAndAssemble(
      FetchFundComparisonPricesUseCaseCommand command, FundComparisonDateRange dateRange) {
    String fundList = String.join(",", command.fundCodes());
    var portCommand =
        new FetchFundComparisonPricesCommand(
            command.policyNo(),
            dateRange.fromDate(),
            dateRange.toDate(),
            fundList,
            command.accountEnv(),
            command.actorUserRole());
    return sourcePort
        .fetchPrices(portCommand)
        .onErrorMap(FundComparisonPricesUseCase::toRetrievalException)
        .map(
            rawData ->
                new FundComparisonPricesResult(
                    dateRange.fromDate(), dateRange.toDate(), command.fundCodes(), rawData));
  }

  private static Throwable toRetrievalException(Throwable exception) {
    return exception instanceof FundComparisonRetrievalException
        ? exception
        : FundComparisonRetrievalException.systemFailure();
  }
}
