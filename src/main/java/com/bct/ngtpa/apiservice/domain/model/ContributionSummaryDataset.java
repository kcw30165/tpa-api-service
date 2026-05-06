package com.bct.ngtpa.apiservice.domain.model;

import java.util.List;

public record ContributionSummaryDataset(
        String currency,
        List<ContributionSource> sources,
        List<ContributionEntry> entries
) {}