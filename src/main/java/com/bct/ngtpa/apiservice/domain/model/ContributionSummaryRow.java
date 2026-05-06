package com.bct.ngtpa.apiservice.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ContributionSummaryRow(
        String dealingDate,
        String coverFrom,
        String coverTo,
        BigDecimal totalAmount,
        Map<String, BigDecimal> amountsBySourceCode
) {

    public String coveringPeriod() {
        return coverFrom + " - " + coverTo;
    }

    public List<ContributionSourceAmount> details(List<ContributionSource> orderedSources) {
        return orderedSources.stream()
                .map(source -> {
                    var amount = amountsBySourceCode.get(source.code());
                    if (amount == null) {
                        return null;
                    }
                    return new ContributionSourceAmount(source, amount);
                })
                .filter(Objects::nonNull)
                .toList();
    }
}