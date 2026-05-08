package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.port.in.GetContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.config.logging.LogExecution;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReportBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GetContributionSummaryService implements GetContributionSummaryUseCase {

    private final ApimContributionSummaryPort apimContributionSummaryPort;
    private final CurrencyMappingService currencyMappingService;
    private final ReferenceDatePort referenceDatePort;
    private final MemberContextPort memberContextPort;

    @Override
        @LogExecution(value = "usecase.getContributionSummary", logArgs = true)
    public Mono<ContributionSummaryReportResult> execute(GetContributionSummaryCommand command) {
        var fromDate = ContributionSummarySupport.parseRequiredDate(command.fromDate(), "fromDate");
        var toDate = ContributionSummarySupport.parseRequiredDate(command.toDate(), "toDate");

        return referenceDatePort.resolveReferenceDate()
                .flatMap(refDate -> {
                    ContributionSummarySupport.validateDateRangeWithinReferenceWindow(fromDate, toDate, refDate);
                    return memberContextPort.resolveMemberContext(MemberContextPurpose.CONTRIBUTIONS)
                            .map(memberContext -> ContributionSummarySupport.newFetchCommand(
                                    command.env(),
                                    command.mbrType(),
                                    ContributionSummarySupport.formatDate(fromDate),
                                    ContributionSummarySupport.formatDate(toDate),
                                    memberContext));
                })
                .flatMap(fetchCommand -> apimContributionSummaryPort.fetchContributionSummary(fetchCommand)
                        .map(ContributionSummaryReportBuilder::build)
                        .map(report -> new ContributionSummaryReportResult(
                                report,
                                new CurrencyDisplay(
                                        currencyMappingService.resolve(
                                                "en",
                                                report.currency(),
                                                fetchCommand.env(),
                                                fetchCommand.trustCode(),
                                                fetchCommand.schemeType()),
                                        currencyMappingService.resolve(
                                                "zh_HK",
                                                report.currency(),
                                                fetchCommand.env(),
                                                fetchCommand.trustCode(),
                                                fetchCommand.schemeType())))));
    }
}