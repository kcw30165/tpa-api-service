package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReportBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ExportContributionSummaryService implements ExportContributionSummaryUseCase {

        private final ApimContributionSummaryPort apimContributionSummaryPort;
        private final CurrencyDisplayPort currencyDisplayPort;
        private final ReferenceDatePort referenceDatePort;
        private final MemberContextPort memberContextPort;

        @Override
        @LogExecution(value = "usecase.exportContributionSummary", logArgs = true)
        public Mono<ContributionSummaryReportResult> execute(ExportContributionSummaryCommand command) {
                return referenceDatePort.resolveReferenceDate()
                                .flatMap(refDate -> memberContextPort
                                                .resolveMemberContext(MemberContextPurpose.CONTRIBUTIONS)
                                                .map(memberContext -> ContributionSummarySupport.newFetchCommand(
                                                                command.env(),
                                                                command.mbrType(),
                                                                ContributionSummarySupport
                                                                                .formatDate(refDate.minusMonths(36)),
                                                                ContributionSummarySupport.formatDate(refDate),
                                                                memberContext)))
                                .flatMap(fetchCommand -> apimContributionSummaryPort
                                                .fetchContributionSummary(fetchCommand)
                                                .map(ContributionSummaryReportBuilder::build)
                                                .map(report -> new ContributionSummaryReportResult(
                                                                report,
                                                                currencyDisplayPort.resolveCurrencyDisplay(
                                                                                report.currency(),
                                                                                fetchCommand.env(),
                                                                                fetchCommand.trustCode(),
                                                                                fetchCommand.schemeType()))));
        }
}