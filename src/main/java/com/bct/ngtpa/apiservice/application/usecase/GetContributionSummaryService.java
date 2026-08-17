package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.in.GetContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReportBuilder;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetContributionSummaryService implements GetContributionSummaryUseCase {
    private final ApimContributionSummaryPort apimContributionSummaryPort;
    private final CurrencyDisplayPort currencyDisplayPort;
    private final ReferenceDatePort referenceDatePort;
    private final ContributionActionPermissionPort contributionActionPermissionPort;
    @Override public Mono<ContributionSummaryReportResult> execute(GetContributionSummaryCommand command) {
        ContributionSummarySupport.validatePagination(command.page(),command.pageSize());
        var from=ContributionSummarySupport.parseRequiredDate(command.fromDate(),"fromDate");
        var to=ContributionSummarySupport.parseRequiredDate(command.toDate(),"toDate");
        return referenceDatePort.resolveReferenceDate(command.accountEnv()).flatMap(ref -> {
            ContributionSummarySupport.validateDateRangeWithinReferenceWindow(from,to,ref);
            var fetch=new FetchContributionSummaryCommand(command.policyNo(), command.certNo(), command.actorUserId(),
                    ContributionSummarySupport.formatDate(from), ContributionSummarySupport.formatDate(to),
                    command.accountEnv(), command.actorUserRole());
            return apimContributionSummaryPort.fetchContributionSummary(fetch).map(ContributionSummaryReportBuilder::build)
                    .map(report -> new ContributionSummaryReportResult(report,
                            currencyDisplayPort.resolveCurrencyDisplay(report.currency(),fetch.accountEnv(),command.trustCode(),command.schemeType()),
                            contributionActionPermissionPort.resolveContributionActions(),command.trustCode(),command.schemeType(),fetch.accountEnv()));
        });
    }
}
