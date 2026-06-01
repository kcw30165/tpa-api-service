package com.bct.ngtpa.apiservice.application.dto;

public record GetContributionSummaryCommand(
        String fromDate,
        String toDate,
        String lang,
        int page,
        int pageSize,
        String accountRef
) {
    public GetContributionSummaryCommand(
            String fromDate,
            String toDate,
            String lang,
            int page,
            int pageSize) {
        this(fromDate, toDate, lang, page, pageSize, null);
    }
}