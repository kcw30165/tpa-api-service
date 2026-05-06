package com.bct.ngtpa.apiservice.application.dto;

public record FetchContributionSummaryCommand(
        String env,
        String mbrType,
        String coverFrom,
        String coverTo,
        String policyNo,
        String certNo,
        String userId,
        String trustCode,
        String schemeType
) {}