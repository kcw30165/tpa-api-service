package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import reactor.core.publisher.Mono;

public interface ExportContributionSummaryUseCase {
    Mono<ContributionSummaryReportResult> execute(ExportContributionSummaryCommand command);
}