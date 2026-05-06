package com.bct.ngtpa.apiservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContributionSummaryReportBuilderTest {

    @Test
    void groupsEntriesByDealingDateAndCoveringPeriodAndOrdersSourcesBySequence() {
        var dataset = new ContributionSummaryDataset(
                "HKD",
                List.of(
                        new ContributionSource("EE", new ContributionLabels("Member", ""), 20),
                        new ContributionSource("ER", new ContributionLabels("Company", ""), 10)),
                List.of(
                        new ContributionEntry("ER", "01/03/2026", "31/03/2026", "01/03/2026", new BigDecimal("17791.75")),
                        new ContributionEntry("EE", "01/03/2026", "31/03/2026", "01/03/2026", new BigDecimal("7116.7")),
                new ContributionEntry("EE", "01/02/2026", "28/02/2026", "01/02/2026", new BigDecimal("400.00"))));

        var report = ContributionSummaryReportBuilder.build(dataset);

        assertEquals(List.of("ER", "EE"), report.sources().stream().map(ContributionSource::code).toList());
        assertEquals(2, report.rows().size());

        var marchRow = report.rows().getFirst();
        assertEquals("01/03/2026", marchRow.dealingDate());
        assertEquals("01/03/2026 - 31/03/2026", marchRow.coveringPeriod());
        assertEquals(new BigDecimal("24908.45"), marchRow.totalAmount());
        assertEquals(new BigDecimal("17791.75"), marchRow.amountsBySourceCode().get("ER"));
        assertEquals(new BigDecimal("7116.7"), marchRow.amountsBySourceCode().get("EE"));
        assertEquals(List.of("ER", "EE"), marchRow.details(report.sources()).stream().map(detail -> detail.source().code()).toList());

        var februaryRow = report.rows().get(1);
        assertEquals(new BigDecimal("400.00"), februaryRow.totalAmount());
        assertEquals(new BigDecimal("400.00"), februaryRow.amountsBySourceCode().get("EE"));
        assertNull(februaryRow.amountsBySourceCode().get("ER"));
    }
}