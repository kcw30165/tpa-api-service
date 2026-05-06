package com.bct.ngtpa.apiservice.application.dto;

import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReport;

public record ContributionSummaryReportResult(
        ContributionSummaryReport report,
        String currencyDisplay
) {}