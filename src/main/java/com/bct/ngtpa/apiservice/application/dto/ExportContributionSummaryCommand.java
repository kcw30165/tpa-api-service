package com.bct.ngtpa.apiservice.application.dto;

public record ExportContributionSummaryCommand(String accountRef) {
	public ExportContributionSummaryCommand() {
		this(null);
	}
}