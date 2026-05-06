package com.bct.ngtpa.apiservice.domain.model;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ContributionSummaryReportBuilder {

    private ContributionSummaryReportBuilder() {
    }

    public static ContributionSummaryReport build(ContributionSummaryDataset dataset) {
        var orderedSources = dataset.sources().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing((ContributionSource source) -> source.sequence() == null ? Integer.MAX_VALUE : source.sequence())
                        .thenComparing(ContributionSource::code, Comparator.nullsLast(String::compareTo)))
                .toList();

        Map<RowKey, RowAccumulator> groupedRows = new LinkedHashMap<>();
        for (var entry : dataset.entries()) {
            if (entry == null) {
                continue;
            }

            var rowKey = new RowKey(entry.dealingDate(), entry.coverFrom(), entry.coverTo());
            var row = groupedRows.computeIfAbsent(rowKey, key -> new RowAccumulator());
            row.totalAmount = row.totalAmount.add(defaultAmount(entry.amount()));
            row.amountsBySourceCode.merge(entry.sourceCode(), defaultAmount(entry.amount()), BigDecimal::add);
        }

        List<ContributionSummaryRow> rows = groupedRows.entrySet().stream()
                .map(entry -> new ContributionSummaryRow(
                        entry.getKey().dealingDate(),
                        entry.getKey().coverFrom(),
                        entry.getKey().coverTo(),
                        entry.getValue().totalAmount,
                        Map.copyOf(entry.getValue().amountsBySourceCode)))
                .toList();

        return new ContributionSummaryReport(dataset.currency(), orderedSources, rows);
    }

    private static BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private record RowKey(String dealingDate, String coverFrom, String coverTo) {}

    private static final class RowAccumulator {
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private final Map<String, BigDecimal> amountsBySourceCode = new LinkedHashMap<>();
    }
}