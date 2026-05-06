package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReportBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ExportContributionSummaryService implements ExportContributionSummaryUseCase {

    private final ApimContributionSummaryPort apimContributionSummaryPort;

    @Override
    public Mono<ContributionSummaryReportResult> execute(ExportContributionSummaryCommand command) {
        // TODO: source ref-date from Progress in non-production once the integration is available.
        var refDate = ContributionSummarySupport.HARDCODED_REF_DATE;
        var coverFrom = refDate.minusMonths(36);
        var fetchCommand = ContributionSummarySupport.newFetchCommand(
                command.env(),
                command.mbrType(),
                ContributionSummarySupport.formatDate(coverFrom),
                ContributionSummarySupport.formatDate(refDate));

        return apimContributionSummaryPort.fetchContributionSummary(fetchCommand)
                .map(ContributionSummaryReportBuilder::build)
                .map(report -> new ContributionSummaryReportResult(
                        report,
                        ContributionSummarySupport.resolveCurrencyDisplay(
                                report.currency(),
                                fetchCommand.trustCode(),
                                fetchCommand.schemeType())));
    }
}