package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.port.in.GetContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.ContributionActionPermissionPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReportBuilder;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetContributionSummaryService implements GetContributionSummaryUseCase {

        private final ApimContributionSummaryPort apimContributionSummaryPort;
        private final CurrencyDisplayPort currencyDisplayPort;
        private final ReferenceDatePort referenceDatePort;
        private final MemberContextPort memberContextPort;
        private final ContributionActionPermissionPort contributionActionPermissionPort;

        @Override
        public Mono<ContributionSummaryReportResult> execute(GetContributionSummaryCommand command) {
                ContributionSummarySupport.validatePagination(command.page(), command.pageSize());

                var fromDate = ContributionSummarySupport.parseRequiredDate(command.fromDate(), "fromDate");
                var toDate = ContributionSummarySupport.parseRequiredDate(command.toDate(), "toDate");

                return referenceDatePort.resolveReferenceDate()
                                .flatMap(refDate -> {
                                        ContributionSummarySupport.validateDateRangeWithinReferenceWindow(fromDate,
                                                        toDate, refDate);
                                        return memberContextPort
                                                        .resolveMemberContext(MemberContextPurpose.CONTRIBUTIONS)
                                                        .map(memberContext -> ContributionSummarySupport
                                                                        .newFetchCommand(
                                                                                        command.env(),
                                                                                        command.mbrType(),
                                                                                        ContributionSummarySupport
                                                                                                        .formatDate(fromDate),
                                                                                        ContributionSummarySupport
                                                                                                        .formatDate(toDate),
                                                                                        memberContext));
                                })
                                .flatMap(fetchCommand -> apimContributionSummaryPort
                                                .fetchContributionSummary(fetchCommand)
                                                .map(ContributionSummaryReportBuilder::build)
                                                .map(report -> new ContributionSummaryReportResult(
                                                                report,
                                                                currencyDisplayPort.resolveCurrencyDisplay(
                                                                                report.currency(),
                                                                                fetchCommand.env(),
                                                                                fetchCommand.trustCode(),
                                                                                fetchCommand.schemeType()),
                                                                contributionActionPermissionPort
                                                                                .resolveContributionActions(),
                                                                fetchCommand.trustCode(),
                                                                fetchCommand.schemeType())));
        }
}