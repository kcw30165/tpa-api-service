package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextProvider;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReportBuilder;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ExportContributionSummaryService implements ExportContributionSummaryUseCase {

        private final ApimContributionSummaryPort apimContributionSummaryPort;
        private final CurrencyDisplayPort currencyDisplayPort;
        private final ReferenceDatePort referenceDatePort;
        private final CurrentPortalAccessContextProvider currentPortalAccessContextProvider;

        @Override
        public Mono<ContributionSummaryReportResult> execute(ExportContributionSummaryCommand command) {
                return referenceDatePort.resolveReferenceDate()
                                .flatMap(refDate -> currentPortalAccessContextProvider.current()
                                                .map(ctx -> ContributionSummarySupport.newFetchCommand(
                                                                ContributionSummarySupport
                                                                                .formatDate(refDate.minusMonths(36)),
                                                                ContributionSummarySupport.formatDate(refDate),
                                                                ctx)))
                                .flatMap(fetchCommand -> apimContributionSummaryPort
                                                .fetchContributionSummary(fetchCommand)
                                                .map(ContributionSummaryReportBuilder::build)
                                                .map(report -> new ContributionSummaryReportResult(
                                                                report,
                                                                currencyDisplayPort.resolveCurrencyDisplay(
                                                                                report.currency(),
                                                                                fetchCommand.accountEnv(),
                                                                                fetchCommand.trustCode(),
                                                                                fetchCommand.schemeType()),
                                                                null,
                                                                fetchCommand.trustCode(),
                                                                fetchCommand.schemeType(),
                                                                fetchCommand.accountEnv())));
        }
}