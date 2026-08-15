package com.bct.ngtpa.apiservice.application.dto;

public record ExportContributionSummaryCommand(
        String accountEnv, String policyNo, String certNo, String actorUserId,
        String trustCode, String schemeType) {
}
