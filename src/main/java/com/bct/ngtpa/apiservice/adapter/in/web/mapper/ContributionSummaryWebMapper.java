package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionWebDisplayConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionSummaryDetailResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionSummaryItemResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionSummaryLabelsResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionSummaryResponse;
import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class ContributionSummaryWebMapper {

    public ContributionSummaryResponse toResponse(
            ContributionSummaryReportResult result,
            ContributionWebDisplayConfig displayConfig) {

        var totalLabels = new ContributionLabels(
                displayConfig.totalLabelEn(),
                displayConfig.totalLabelZh());

        var currencyDisplay = result.currencyDisplay();
        var currencyEn = currencyDisplay == null ? "" : currencyDisplay.en();
        var currencyZh = currencyDisplay == null ? "" : currencyDisplay.zh();

        return new ContributionSummaryResponse(
                result.report().rows().stream()
                        .map(row -> {
                            List<ContributionSummaryDetailResponse> details = new ArrayList<>();

                            details.add(new ContributionSummaryDetailResponse(
                                    toLabelsResponse(totalLabels),
                                    formatAmount(currencyEn, row.totalAmount()),
                                    formatAmount(currencyZh, row.totalAmount())));

                            row.details(result.report().sources()).forEach(detail -> details.add(
                                    new ContributionSummaryDetailResponse(
                                            toLabelsResponse(detail.source().labels()),
                                            formatAmount(currencyEn, detail.amount()),
                                            formatAmount(currencyZh, detail.amount()))));

                            return new ContributionSummaryItemResponse(
                                    row.dealingDate(),
                                    row.coveringPeriod(),
                                    formatAmount(currencyEn, row.totalAmount()),
                                    formatAmount(currencyZh, row.totalAmount()),
                                    List.copyOf(details));
                        })
                        .toList());
    }

    ContributionSummaryLabelsResponse toLabelsResponse(ContributionLabels labels) {
        return new ContributionSummaryLabelsResponse(labels.en(), labels.zh());
    }

    String formatAmount(String currencyDisplay, BigDecimal amount) {
        var normalizedAmount = amount == null ? BigDecimal.ZERO : amount.stripTrailingZeros();
        var amountText = normalizedAmount.toPlainString();

        if (currencyDisplay == null || currencyDisplay.isBlank()) {
            return amountText;
        }

        return currencyDisplay.trim() + " " + amountText;
    }
}
