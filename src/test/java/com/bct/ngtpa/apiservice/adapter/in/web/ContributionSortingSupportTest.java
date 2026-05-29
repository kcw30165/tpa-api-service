package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionSummaryProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionWebDisplayConfigProvider;
import com.bct.ngtpa.apiservice.adapter.in.web.sort.ApplySortsAspect;
import com.bct.ngtpa.apiservice.adapter.in.web.sort.SortEngine;
import com.bct.ngtpa.apiservice.application.dto.ContributionActions;
import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;
import com.bct.ngtpa.apiservice.domain.model.ContributionSource;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReport;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ContributionSortingSupport} verifying contribution-specific sort rules.
 *
 * <p>Uses {@link AspectJProxyFactory} to apply {@link ApplySortsAspect} on a real
 * {@link ContributionSortingSupport} instance so that the {@link com.bct.ngtpa.apiservice.adapter.in.web.sort.ApplySorts}
 * annotation is intercepted without a full Spring context.
 */
class ContributionSortingSupportTest {

    private ContributionSortingSupport sortingSupport;

    @BeforeEach
    void setUp() {
        var sortEngine = new SortEngine();
        var aspect = new ApplySortsAspect(sortEngine);
        var factory = new AspectJProxyFactory(new ContributionSortingSupport());
        factory.addAspect(aspect);
        sortingSupport = factory.getProxy();
    }

    // ─── report.rows sorting ──────────────────────────────────────────────────

    @Test
    void sortsRowsByDealingDateDesc() {
        var result = resultWithRows(
                row("01/01/2026", "01/01/2026", "31/01/2026"),
                row("01/03/2026", "01/03/2026", "31/03/2026"),
                row("01/02/2026", "01/02/2026", "28/02/2026"));

        var sorted = sortingSupport.sort(result);

        var dates = sorted.report().rows().stream().map(ContributionSummaryRow::dealingDate).toList();
        assertEquals(List.of("01/03/2026", "01/02/2026", "01/01/2026"), dates);
    }

    @Test
    void sortsByDealingDateDescThenCoverFromDescForSameDealingDate() {
        var result = resultWithRows(
                row("15/03/2026", "01/03/2026", "31/03/2026"),
                row("15/03/2026", "01/01/2026", "31/01/2026"),
                row("15/03/2026", "01/02/2026", "28/02/2026"));

        var sorted = sortingSupport.sort(result);

        var coverFroms = sorted.report().rows().stream().map(ContributionSummaryRow::coverFrom).toList();
        assertEquals(List.of("01/03/2026", "01/02/2026", "01/01/2026"), coverFroms);
    }

    @Test
    void sortsByDealingDateDescThenCoverToDescForSameDealingDateAndCoverFrom() {
        var result = resultWithRows(
                row("15/03/2026", "01/03/2026", "15/03/2026"),
                row("15/03/2026", "01/03/2026", "31/03/2026"),
                row("15/03/2026", "01/03/2026", "28/03/2026"));

        var sorted = sortingSupport.sort(result);

        var coverTos = sorted.report().rows().stream().map(ContributionSummaryRow::coverTo).toList();
        assertEquals(List.of("31/03/2026", "28/03/2026", "15/03/2026"), coverTos);
    }

    @Test
    void placesNullDealingDateLast() {
        var result = resultWithRows(
                row(null,         "01/01/2026", "31/01/2026"),
                row("01/03/2026", "01/03/2026", "31/03/2026"),
                row(null,         "01/02/2026", "28/02/2026"));

        var sorted = sortingSupport.sort(result);

        var rows = sorted.report().rows();
        assertEquals("01/03/2026", rows.get(0).dealingDate());
        assertTrue(rows.get(1).dealingDate() == null || rows.get(1).dealingDate().isEmpty()
                || rows.get(2).dealingDate() == null || rows.get(2).dealingDate().isEmpty());
    }

    @Test
    void placesInvalidDealingDateLast() {
        var result = resultWithRows(
                row("not-a-date",  "01/01/2026", "31/01/2026"),
                row("01/04/2026",  "01/04/2026", "30/04/2026"),
                row("INVALID",     "01/02/2026", "28/02/2026"));

        var sorted = sortingSupport.sort(result);

        assertEquals("01/04/2026", sorted.report().rows().get(0).dealingDate());
        // bad dates go last
        var badDates = sorted.report().rows().subList(1, 3).stream()
                .map(ContributionSummaryRow::dealingDate).toList();
        assertTrue(badDates.containsAll(List.of("not-a-date", "INVALID")));
    }

    // ─── report.sources sorting ───────────────────────────────────────────────

    @Test
    void sortsSourcesBySequenceAscThenCodeAsc() {
        var unsortedSources = List.of(
                new ContributionSource("EE", new ContributionLabels("Member", ""), 20),
                new ContributionSource("ER", new ContributionLabels("Company", ""), 10),
                new ContributionSource("VE", new ContributionLabels("Voluntary", ""), 30));

        var result = resultWithSources(unsortedSources,
                row("01/03/2026", "01/03/2026", "31/03/2026"));

        var sorted = sortingSupport.sort(result);

        var codes = sorted.report().sources().stream().map(ContributionSource::code).toList();
        assertEquals(List.of("ER", "EE", "VE"), codes);
    }

    @Test
    void sortsSourcesByCodeAscWhenSequencesAreTied() {
        var sources = List.of(
                new ContributionSource("ZZ", new ContributionLabels("Zzz", ""), 10),
                new ContributionSource("AA", new ContributionLabels("Aaa", ""), 10));

        var result = resultWithSources(sources, row("01/03/2026", "01/03/2026", "31/03/2026"));

        var sorted = sortingSupport.sort(result);

        var codes = sorted.report().sources().stream().map(ContributionSource::code).toList();
        assertEquals(List.of("AA", "ZZ"), codes);
    }

    @Test
    void progressTotalRowWithSmallestSeqAppearsFirstAfterSorting() {
        // Progress provides total with the smallest sequence number;
        // after sorting by sequence ASC the total source naturally appears first.
        var sources = List.of(
                new ContributionSource("EE", new ContributionLabels("Member", ""), 20),
                new ContributionSource("TOTAL", new ContributionLabels("Total", ""), 1), // smallest
                new ContributionSource("ER", new ContributionLabels("Company", ""), 10));

        var result = resultWithSources(sources, row("01/03/2026", "01/03/2026", "31/03/2026"));

        var sorted = sortingSupport.sort(result);

        // TOTAL has seq=1, so it naturally appears first – no label-based hardcoding needed
        assertEquals("TOTAL", sorted.report().sources().get(0).code());
        assertEquals("ER",    sorted.report().sources().get(1).code());
        assertEquals("EE",    sorted.report().sources().get(2).code());
    }

    // ─── Immutability ─────────────────────────────────────────────────────────

    @Test
    void originalResultIsNotMutated() {
        var original = resultWithRows(
                row("01/01/2026", "01/01/2026", "31/01/2026"),
                row("01/03/2026", "01/03/2026", "31/03/2026"));

        var originalFirstDate = original.report().rows().get(0).dealingDate();

        sortingSupport.sort(original);

        // The original result must remain unchanged
        assertEquals(originalFirstDate, original.report().rows().get(0).dealingDate());
    }

    // ─── Excel export sorting ─────────────────────────────────────────────────

    @Test
    void excelRowsAreOrderedByDealingDateDescThenCoverFromDesc() throws Exception {
        var unsorted = new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(
                                new ContributionSource("ER", new ContributionLabels("Company", ""), 10),
                                new ContributionSource("EE", new ContributionLabels("Member", ""), 20)),
                        List.of(
                                row("01/01/2026", "01/01/2026", "31/01/2026", BigDecimal.ONE),
                                row("01/03/2026", "01/03/2026", "31/03/2026", BigDecimal.TEN),
                                row("01/02/2026", "01/02/2026", "28/02/2026", new BigDecimal("5")))),
                new CurrencyDisplay("HKD", "港元"),
                new ContributionActions(true),
                "", "", "");

        // Sort then export
        var sorted = sortingSupport.sort(unsorted);
        var provider = new ContributionWebDisplayConfigProvider(new ContributionSummaryProperties());
        var exporter = new ContributionSummaryWorkbookExporter(provider);
        byte[] bytes = exporter.write(sorted);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            // Data rows start at index 1 (row 0 is header)
            var row1 = sheet.getRow(1).getCell(0).getStringCellValue(); // first data row
            var row2 = sheet.getRow(2).getCell(0).getStringCellValue();
            var row3 = sheet.getRow(3).getCell(0).getStringCellValue();

            // Expected order: Mar (latest) → Feb → Jan (oldest)
            assertEquals("01/03/2026", row1);
            assertEquals("01/02/2026", row2);
            assertEquals("01/01/2026", row3);
        }
    }

    @Test
    void excelRowsAreSortedByCoverFromDescWhenSameDealingDate() throws Exception {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(new ContributionSource("ER", new ContributionLabels("Company", ""), 10)),
                        List.of(
                                row("15/03/2026", "01/01/2026", "31/01/2026", BigDecimal.ONE),
                                row("15/03/2026", "01/03/2026", "31/03/2026", BigDecimal.TEN),
                                row("15/03/2026", "01/02/2026", "28/02/2026", new BigDecimal("5")))),
                new CurrencyDisplay("HKD", "港元"),
                new ContributionActions(true),
                "", "", "");

        var sorted = sortingSupport.sort(result);
        var provider = new ContributionWebDisplayConfigProvider(new ContributionSummaryProperties());
        var exporter = new ContributionSummaryWorkbookExporter(provider);
        byte[] bytes = exporter.write(sorted);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            // Period column (index 1) contains "coverFrom - coverTo"
            var period1 = sheet.getRow(1).getCell(1).getStringCellValue();
            var period2 = sheet.getRow(2).getCell(1).getStringCellValue();
            var period3 = sheet.getRow(3).getCell(1).getStringCellValue();

            assertTrue(period1.contains("01/03/2026"), "First row should have the latest coverFrom");
            assertTrue(period2.contains("01/02/2026"));
            assertTrue(period3.contains("01/01/2026"));
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ContributionSummaryReportResult resultWithRows(ContributionSummaryRow... rows) {
        var sources = List.of(
                new ContributionSource("ER", new ContributionLabels("Company", ""), 10),
                new ContributionSource("EE", new ContributionLabels("Member", ""), 20));
        var report = new ContributionSummaryReport("HKD", sources, List.of(rows));
        return new ContributionSummaryReportResult(
                report, new CurrencyDisplay("HKD", "港元"), new ContributionActions(true), "", "", "");
    }

    private ContributionSummaryReportResult resultWithSources(
            List<ContributionSource> sources, ContributionSummaryRow... rows) {
        var report = new ContributionSummaryReport("HKD", sources, List.of(rows));
        return new ContributionSummaryReportResult(
                report, new CurrencyDisplay("HKD", "港元"), new ContributionActions(true), "", "", "");
    }

    private ContributionSummaryRow row(String dealingDate, String coverFrom, String coverTo) {
        return row(dealingDate, coverFrom, coverTo, new BigDecimal("100.00"));
    }

    private ContributionSummaryRow row(String dealingDate, String coverFrom, String coverTo,
                                       BigDecimal amount) {
        return new ContributionSummaryRow(
                dealingDate, coverFrom, coverTo, amount,
                new LinkedHashMap<>(Map.of("ER", amount)));
    }

    private ContributionSummaryRow row(String dealingDate, String coverFrom, String coverTo,
                                       BigDecimal amount, Map<String, BigDecimal> amountsBySource) {
        return new ContributionSummaryRow(dealingDate, coverFrom, coverTo, amount,
                new LinkedHashMap<>(amountsBySource));
    }
}
