package com.bct.ngtpa.apiservice.adapter.in.web.response;

/**
 * Pagination metadata for the contribution list response.
 */
public record ContributionPaginationResponse(
        int page,
        int pageSize,
        int totalRecords,
        boolean hasNextPage
) {}
