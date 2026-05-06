package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.config.ContributionSummaryProperties;
import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record ContributionSummaryResponse(List<ContributionSummaryItemResponse> contributions) {

    public static ContributionSummaryResponse from(
            ContributionSummaryReportResult result,
            ContributionSummaryProperties properties) {
        var totalLabels = new ContributionLabels(
                properties.getTotalLabel().getEn(),
                properties.getTotalLabel().getZh());
        var currencyDisplay = result.currencyDisplay();
        var currencyEn = currencyDisplay == null ? "" : currencyDisplay.en();
        var currencyZh = currencyDisplay == null ? "" : currencyDisplay.zh();

        return new ContributionSummaryResponse(result.report().rows().stream()
                .map(row -> {
                    List<ContributionSummaryDetailResponse> details = new ArrayList<>();
                    details.add(new ContributionSummaryDetailResponse(
                            ContributionSummaryLabelsResponse.from(totalLabels, currencyDisplay),
                            amountValue(row.totalAmount())));
                    row.details(result.report().sources()).forEach(detail -> details.add(
                            new ContributionSummaryDetailResponse(
                                    ContributionSummaryLabelsResponse.from(detail.source().labels(), currencyDisplay),
                                    amountValue(detail.amount()))));

                    return new ContributionSummaryItemResponse(
                            row.dealingDate(),
                            row.coveringPeriod(),
                            formatTotalContribution(currencyEn, row.totalAmount()),
                            formatTotalContribution(currencyZh, row.totalAmount()),
                            List.copyOf(details));
                })
                .toList());
    }

    private static String formatTotalContribution(String currencyDisplay, BigDecimal amount) {
        var normalizedAmount = amount == null ? BigDecimal.ZERO : amount.stripTrailingZeros();
        return (currencyDisplay == null ? "" : currencyDisplay + " ") + normalizedAmount.toPlainString();
    }

    private static BigDecimal amountValue(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}