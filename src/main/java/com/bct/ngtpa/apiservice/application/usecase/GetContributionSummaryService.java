package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.in.GetContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReportBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GetContributionSummaryService implements GetContributionSummaryUseCase {

    private final ApimContributionSummaryPort apimContributionSummaryPort;

    @Override
    public Mono<ContributionSummaryReportResult> execute(GetContributionSummaryCommand command) {
        var fromDate = ContributionSummarySupport.parseRequiredDate(command.fromDate(), "fromDate");
        var toDate = ContributionSummarySupport.parseRequiredDate(command.toDate(), "toDate");
        var fetchCommand = ContributionSummarySupport.newFetchCommand(
                command.env(),
                command.mbrType(),
                ContributionSummarySupport.formatDate(fromDate),
                ContributionSummarySupport.formatDate(toDate));

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