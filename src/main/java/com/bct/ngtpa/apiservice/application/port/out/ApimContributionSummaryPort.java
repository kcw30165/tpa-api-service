package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset;
import reactor.core.publisher.Mono;

public interface ApimContributionSummaryPort {
    Mono<ContributionSummaryDataset> fetchContributionSummary(FetchContributionSummaryCommand command);
}