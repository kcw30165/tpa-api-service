package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import reactor.core.publisher.Mono;

public interface GetContributionSummaryUseCase {
    Mono<ContributionSummaryReportResult> execute(GetContributionSummaryCommand command);
}