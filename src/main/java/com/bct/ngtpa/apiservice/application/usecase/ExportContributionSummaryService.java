package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReportBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ExportContributionSummaryService implements ExportContributionSummaryUseCase {

    private final ApimContributionSummaryPort apimContributionSummaryPort;
    private final CurrencyMappingService currencyMappingService;
    private final ReferenceDatePort referenceDatePort;

    @Override
    public Mono<ContributionSummaryReportResult> execute(ExportContributionSummaryCommand command) {
        return referenceDatePort.resolveReferenceDate(command.env())
                .map(refDate -> ContributionSummarySupport.newFetchCommand(
                        command.env(),
                        command.mbrType(),
                        ContributionSummarySupport.formatDate(refDate.minusMonths(36)),
                        ContributionSummarySupport.formatDate(refDate)))
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