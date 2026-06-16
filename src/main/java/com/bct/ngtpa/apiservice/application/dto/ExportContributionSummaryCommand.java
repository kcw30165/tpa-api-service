package com.bct.ngtpa.apiservice.application.dto;

public record ExportContributionSummaryCommand() {
    /**
     * Compatibility overload for older focused tests. The selected account is now
     * supplied by CurrentPortalAccessContextProvider, not by this command.
     */
    public ExportContributionSummaryCommand(String ignoredLegacySelectedAccount) {
        this();
    }
}
