package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;

public record ContributionSummaryLabelsResponse(String en, String zh, String currencyEn, String currencyZh) {

    public static ContributionSummaryLabelsResponse from(ContributionLabels labels, CurrencyDisplay currencyDisplay) {
        return new ContributionSummaryLabelsResponse(
                labels.en(),
                labels.zh(),
                currencyDisplay == null ? "" : safe(currencyDisplay.en()),
                currencyDisplay == null ? "" : safe(currencyDisplay.zh()));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}