package com.bct.ngtpa.apiservice.adapter.in.web.response;

/**
 * A single contribution list item in the new API contract.
 */
public record ContributionItemResponse(
        String itemId,
        ContributionItemType itemType,
        ContributionPeriodResponse period,
        ContributionDateValueResponse dealingDate,
        ContributionCurrencyValueResponse currency,
        ContributionTotalContributionResponse totalContribution,
        ContributionBreakdownResponse breakdown
) {}
