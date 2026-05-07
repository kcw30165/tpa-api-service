package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public record ContributionSummaryItemResponse(
        String dealingDate,
        String coveringPeriod,
        String totalContributionEn,
        String totalContributionZh,
        List<ContributionSummaryDetailResponse> details
) {}