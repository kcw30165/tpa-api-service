package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public record ContributionSummaryItemResponse(
        String dealingDate,
        String coveringPeriod,
        String totalContribution,
        List<ContributionSummaryDetailResponse> details
) {}