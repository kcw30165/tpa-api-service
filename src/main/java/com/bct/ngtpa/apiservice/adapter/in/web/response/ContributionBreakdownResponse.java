package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

/**
 * Contribution breakdown containing ordered rows (total first, then by source).
 */
public record ContributionBreakdownResponse(List<ContributionBreakdownRowResponse> rows) {}
