package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReportBuilder;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ExportContributionSummaryService implements ExportContributionSummaryUseCase {
  private final ApimContributionSummaryPort apimContributionSummaryPort;
  private final CurrencyDisplayPort currencyDisplayPort;
  private final ReferenceDatePort referenceDatePort;

  @Override
  public Mono<ContributionSummaryReportResult> execute(ExportContributionSummaryCommand command) {
    return referenceDatePort
        .resolveReferenceDate(command.accountEnv())
        .flatMap(
            ref -> {
              var fetch =
                  new FetchContributionSummaryCommand(
                      command.policyNo(),
                      command.certNo(),
                      command.actorUserId(),
                      ContributionSummarySupport.formatDate(ref.minusMonths(36)),
                      ContributionSummarySupport.formatDate(ref),
                      command.accountEnv(),
                      command.actorUserRole());
              return apimContributionSummaryPort
                  .fetchContributionSummary(fetch)
                  .map(ContributionSummaryReportBuilder::build)
                  .map(
                      report ->
                          new ContributionSummaryReportResult(
                              report,
                              currencyDisplayPort.resolveCurrencyDisplay(
                                  report.currency(),
                                  fetch.accountEnv(),
                                  command.trustCode(),
                                  command.schemeType()),
                              null,
                              command.trustCode(),
                              command.schemeType(),
                              fetch.accountEnv()));
            });
  }
}
