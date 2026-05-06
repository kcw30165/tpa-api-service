package com.bct.ngtpa.apiservice.application.dto;

public record ExportContributionSummaryCommand(
        String env,
        String mbrType
) {}