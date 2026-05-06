package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;

public record ContributionSummaryLabelsResponse(String en, String zh) {

    public static ContributionSummaryLabelsResponse from(ContributionLabels labels) {
        return new ContributionSummaryLabelsResponse(labels.en(), labels.zh());
    }
}