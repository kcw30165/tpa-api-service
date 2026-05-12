package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

/**
 * Top-level response for GET /api/v1/contributions.
 */
public record ContributionListResponse(
        ContributionActionsResponse actions,
        List<ContributionItemResponse> items,
        ContributionPaginationResponse pagination
) {}
