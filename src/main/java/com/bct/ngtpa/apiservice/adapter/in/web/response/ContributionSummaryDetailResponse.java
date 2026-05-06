package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.math.BigDecimal;

public record ContributionSummaryDetailResponse(
        ContributionSummaryLabelsResponse labels,
        BigDecimal amount
) {}