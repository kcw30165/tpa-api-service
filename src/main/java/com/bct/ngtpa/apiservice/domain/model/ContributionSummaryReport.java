package com.bct.ngtpa.apiservice.domain.model;

import java.util.List;

public record ContributionSummaryReport(
        String currency,
        List<ContributionSource> sources,
        List<ContributionSummaryRow> rows
) {}