package com.bct.ngtpa.apiservice.adapter.in.web.response;

/**
 * A single breakdown row: a label and its associated amount.
 */
public record ContributionBreakdownRowResponse(
        String label,
        ContributionAmountValueResponse amount
) {}
