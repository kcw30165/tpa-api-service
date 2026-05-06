package com.bct.ngtpa.apiservice.adapter.in.web.response;

public record ContributionSummaryDetailResponse(
        ContributionSummaryLabelsResponse labels,
        String amount
) {}