package com.bct.ngtpa.apiservice.application.dto;

public record GetContributionSummaryCommand(
        String env,
        String mbrType,
        String fromDate,
        String toDate
) {}