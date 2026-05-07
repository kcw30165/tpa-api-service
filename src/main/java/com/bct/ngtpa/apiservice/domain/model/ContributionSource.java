package com.bct.ngtpa.apiservice.domain.model;

public record ContributionSource(
        String code,
        ContributionLabels labels,
        Integer sequence
) {}