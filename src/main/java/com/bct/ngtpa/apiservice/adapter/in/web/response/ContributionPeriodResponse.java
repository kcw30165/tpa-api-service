package com.bct.ngtpa.apiservice.adapter.in.web.response;

/**
 * Period covered by a contribution item, expressed as from/to date pairs.
 */
public record ContributionPeriodResponse(
        ContributionDateValueResponse fromDate,
        ContributionDateValueResponse toDate
) {}
