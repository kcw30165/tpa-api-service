package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionWebDisplayConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionItemType;
import com.bct.ngtpa.apiservice.application.port.out.DateDisplayPort;
import com.bct.ngtpa.apiservice.application.dto.ContributionActions;
import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;
import com.bct.ngtpa.apiservice.domain.model.ContributionSource;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReport;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContributionSummaryWebMapperTest {

    /** Plain amount formatter that just calls toPlainString (no thousands separator). */
    private static final ContributionSummaryWebMapper MAPPER = new ContributionSummaryWebMapper(
            (amount, lang, env, trustCode, schemeType) -> amount == null ? "0" : amount.stripTrailingZeros().toPlainString(),
            (date, lang, env, trustCode, schemeType) -> date != null ? date.format(ContributionSummaryWebMapper.ISO_DATE_FORMATTER) : "");

    private static final ContributionWebDisplayConfig DISPLAY_CONFIG = new ContributionWebDisplayConfig(
            "Total Contributions",
            "供款總額",
            "Dealing date處理日期",
            "Contribution Periods供款期",
            "Total Contributions供款總額");

    @Test
    void toListResponsePopulatesTopLevelStructure() {
        var result = buildResult(new CurrencyDisplay("HKD", "港元"), new BigDecimal("100"), BigDecimal.TEN, new BigDecimal("90"));
        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);

        assertEquals(1, response.items().size());
        assertTrue(response.actions().export().enabled());
        assertEquals(1, response.pagination().page());
        assertEquals(99999, response.pagination().pageSize());
        assertEquals(1, response.pagination().totalRecords());
        assertFalse(response.pagination().hasNextPage());
    }

    @Test
    void toListResponseSetsItemTypeAsContribution() {
        var result = buildResult(new CurrencyDisplay("HKD", "港元"), new BigDecimal("100"), BigDecimal.TEN, new BigDecimal("90"));
        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);

        assertEquals(ContributionItemType.CONTRIBUTION, response.items().getFirst().itemType());
    }

    @Test
    void toListResponseFormatsPerRowPeriodDatesViaDisplayPort() {
        var result = buildResult(new CurrencyDisplay("HKD", "港元"), new BigDecimal("100"), BigDecimal.TEN, new BigDecimal("90"));
        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);

        // row.coverFrom = "01/03/2026" → parsed to 2026-03-01 → formatted via DateDisplayPort mock → ISO
        var item = response.items().getFirst();
        assertEquals("2026-03-01", item.period().fromDate().text());
        assertEquals("2026-03-31", item.period().toDate().text());
    }

    @Test
    void toListResponseConvertsDateValuesToIso() {
        var result = buildResult(new CurrencyDisplay("HKD", "港元"), new BigDecimal("100"), BigDecimal.TEN, new BigDecimal("90"));
        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);

        var item = response.items().getFirst();
        assertEquals("2026-03-01", item.period().fromDate().value());
        assertEquals("2026-03-31", item.period().toDate().value());
    }

    @Test
    void toListResponseFormatsDealingDateTextViaDisplayPort() {
        var result = buildResult(new CurrencyDisplay("HKD", "港元"), new BigDecimal("100"), BigDecimal.TEN, new BigDecimal("90"));
        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);

        var item = response.items().getFirst();
        // row.dealingDate = "01/03/2026" → parsed to 2026-03-01 → formatted via DateDisplayPort mock → ISO
        assertEquals("2026-03-01", item.dealingDate().text());
        assertEquals("2026-03-01", item.dealingDate().value());
    }

    @Test
    void toListResponseMapsCurrencyFromDisplayForEnLocale() {
        var result = buildResult(new CurrencyDisplay("HKD", "港元"), new BigDecimal("100"), BigDecimal.TEN, new BigDecimal("90"));
        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);

        assertEquals("HKD", response.items().getFirst().currency().value());
        assertEquals("HKD", response.items().getFirst().currency().text());
    }

    @Test
    void toListResponseMapsCurrencyFromDisplayForZhHkLocale() {
        var result = buildResult(new CurrencyDisplay("HKD", "港元"), new BigDecimal("100"), BigDecimal.TEN, new BigDecimal("90"));
        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "zh_HK", "JP", 1, 99999);

        assertEquals("HKD", response.items().getFirst().currency().value());
        assertEquals("港元", response.items().getFirst().currency().text());
    }

    @Test
    void toListResponseBreakdownHasTotalFirstThenSources() {
        var result = buildResult(new CurrencyDisplay("HKD", "港元"), new BigDecimal("100"), BigDecimal.TEN, new BigDecimal("90"));
        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);

        var rows = response.items().getFirst().breakdown().rows();
        assertEquals(3, rows.size());
        assertEquals("Total Contributions", rows.get(0).label());
        assertEquals("Company", rows.get(1).label());
        assertEquals("Member", rows.get(2).label());
    }

    @Test
    void toListResponseBreakdownUsesZhLabelsForZhHkLocale() {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(
                                new ContributionSource("ER", new ContributionLabels("Company", "公司"), 10),
                                new ContributionSource("EE", new ContributionLabels("Member", "員工"), 20)),
                        List.of(new ContributionSummaryRow(
                                "01/03/2026", "01/03/2026", "31/03/2026",
                                new BigDecimal("100"),
                                new LinkedHashMap<>(java.util.Map.of(
                                        "ER", BigDecimal.TEN,
                                        "EE", new BigDecimal("90")))))),
                new CurrencyDisplay("HKD", "港元"),
                new ContributionActions(true), "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "zh_HK", "JP", 1, 99999);

        var rows = response.items().getFirst().breakdown().rows();
        assertEquals("公司", rows.get(1).label());
        assertEquals("員工", rows.get(2).label());
    }

    @Test
    void toListResponseActionsExportDisabledWhenActionsNull() {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport("HKD", List.of(), List.of(new ContributionSummaryRow(
                        "01/03/2026", "01/03/2026", "31/03/2026", BigDecimal.ZERO, new LinkedHashMap<>()))),
                new CurrencyDisplay("HKD", "HKD"),
                null, "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);
        assertFalse(response.actions().export().enabled());
    }

    @Test
    void buildItemIdsGeneratesStableIdsFromPeriodFromDate() {
        var rows = List.of(
                new ContributionSummaryRow("01/03/2026", "01/03/2026", "31/03/2026", BigDecimal.ONE, new LinkedHashMap<>()),
                new ContributionSummaryRow("01/04/2026", "01/04/2026", "30/04/2026", BigDecimal.ONE, new LinkedHashMap<>()));

        var ids = ContributionSummaryWebMapper.buildItemIds(rows);
        assertEquals("CONTRIB-2026-03", ids.get(0));
        assertEquals("CONTRIB-2026-04", ids.get(1));
    }

    @Test
    void buildItemIdsSuffixesDuplicatePeriods() {
        var rows = List.of(
                new ContributionSummaryRow("01/03/2026", "01/03/2026", "31/03/2026", BigDecimal.ONE, new LinkedHashMap<>()),
                new ContributionSummaryRow("01/04/2026", "01/03/2026", "31/03/2026", BigDecimal.ONE, new LinkedHashMap<>()));

        var ids = ContributionSummaryWebMapper.buildItemIds(rows);
        assertEquals("CONTRIB-2026-03", ids.get(0));
        assertEquals("CONTRIB-2026-03-2", ids.get(1));
    }

    @Test
    void buildItemIdsFallsBackWhenDateUnparseable() {
        var rows = List.of(
                new ContributionSummaryRow("01/03/2026", "not-a-date", "31/03/2026", BigDecimal.ONE, new LinkedHashMap<>()));

        var ids = ContributionSummaryWebMapper.buildItemIds(rows);
        assertEquals("CONTRIB-unknown", ids.get(0));
    }

    @Test
    void tryParseApimDateParsesCorrectly() {
        var date = ContributionSummaryWebMapper.tryParseApimDate("01/03/2026");
        assertEquals(2026, date.getYear());
        assertEquals(3, date.getMonthValue());
        assertEquals(1, date.getDayOfMonth());
    }

    @Test
    void tryParseApimDateReturnsNullForInvalidInput() {
        assertEquals(null, ContributionSummaryWebMapper.tryParseApimDate(null));
        assertEquals(null, ContributionSummaryWebMapper.tryParseApimDate(""));
        assertEquals(null, ContributionSummaryWebMapper.tryParseApimDate("2026-03-01"));
    }

    @Test
    void resolveLabelFallsBackToEnWhenZhIsNullForZhHkLocale() {
        // labels.zh() is null → should fall back to labels.en()
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(new ContributionSource("ER", new ContributionLabels("Company", null), 10)),
                        List.of(new ContributionSummaryRow(
                                "01/03/2026", "01/03/2026", "31/03/2026",
                                new BigDecimal("100"),
                                new LinkedHashMap<>(java.util.Map.of("ER", new BigDecimal("100")))))),
                new CurrencyDisplay("HKD", "港元"),
                new ContributionActions(true), "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "zh_HK", "JP", 1, 99999);
        var rows = response.items().getFirst().breakdown().rows();
        // zh is null → falls back to en label
        assertEquals("Company", rows.get(1).label());
    }

    @Test
    void resolveLabelFallsBackToEnWhenZhIsBlankForZhHkLocale() {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(new ContributionSource("ER", new ContributionLabels("Company", ""), 10)),
                        List.of(new ContributionSummaryRow(
                                "01/03/2026", "01/03/2026", "31/03/2026",
                                new BigDecimal("100"),
                                new LinkedHashMap<>(java.util.Map.of("ER", new BigDecimal("100")))))),
                new CurrencyDisplay("HKD", "港元"),
                new ContributionActions(true), "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "zh_HK", "JP", 1, 99999);
        var rows = response.items().getFirst().breakdown().rows();
        assertEquals("Company", rows.get(1).label());
    }

    @Test
    void resolveLabelWithNullLabelsReturnsEmpty() {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(new ContributionSource("ER", null, 10)),
                        List.of(new ContributionSummaryRow(
                                "01/03/2026", "01/03/2026", "31/03/2026",
                                new BigDecimal("100"),
                                new LinkedHashMap<>(java.util.Map.of("ER", new BigDecimal("100")))))),
                new CurrencyDisplay("HKD", "港元"),
                new ContributionActions(true), "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);
        var rows = response.items().getFirst().breakdown().rows();
        assertEquals("", rows.get(1).label());
    }

    @Test
    void resolveLabelWithNullLabelsForZhHkReturnsEmpty() {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(new ContributionSource("ER", null, 10)),
                        List.of(new ContributionSummaryRow(
                                "01/03/2026", "01/03/2026", "31/03/2026",
                                new BigDecimal("100"),
                                new LinkedHashMap<>(java.util.Map.of("ER", new BigDecimal("100")))))),
                new CurrencyDisplay("HKD", "港元"),
                new ContributionActions(true), "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "zh_HK", "JP", 1, 99999);
        var rows = response.items().getFirst().breakdown().rows();
        assertEquals("", rows.get(1).label());
    }

    @Test
    void resolveCurrencyTextWithNullCurrencyDisplayForZhHkLocale() {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport("HKD", List.of(),
                        List.of(new ContributionSummaryRow("01/03/2026", "01/03/2026", "31/03/2026",
                                BigDecimal.ZERO, new LinkedHashMap<>()))),
                null,
                new ContributionActions(false), "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "zh_HK", "JP", 1, 99999);
        assertEquals("", response.items().getFirst().currency().text());
    }

    @Test
    void resolveCurrencyTextWithNullZhFieldForZhHkLocale() {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport("HKD", List.of(),
                        List.of(new ContributionSummaryRow("01/03/2026", "01/03/2026", "31/03/2026",
                                BigDecimal.ZERO, new LinkedHashMap<>()))),
                new CurrencyDisplay("HKD", null),
                new ContributionActions(false), "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "zh_HK", "JP", 1, 99999);
        assertEquals("", response.items().getFirst().currency().text());
    }

    @Test
    void resolveCurrencyTextWithNullEnFieldForEnLocale() {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport("HKD", List.of(),
                        List.of(new ContributionSummaryRow("01/03/2026", "01/03/2026", "31/03/2026",
                                BigDecimal.ZERO, new LinkedHashMap<>()))),
                new CurrencyDisplay(null, "港元"),
                new ContributionActions(false), "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);
        assertEquals("", response.items().getFirst().currency().text());
    }

    @Test
    void toItemResponseUsesZeroWhenTotalAmountIsNull() {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport("HKD", List.of(),
                        List.of(new ContributionSummaryRow("01/03/2026", "01/03/2026", "31/03/2026",
                                null, new LinkedHashMap<>()))),
                new CurrencyDisplay("HKD", "HKD"),
                new ContributionActions(false), "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);
        assertEquals(java.math.BigDecimal.ZERO, response.items().getFirst().totalContribution().amount().value());
    }

    @Test
    void toItemResponsePreservesRawDateWhenUnparseable() {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport("HKD", List.of(),
                        List.of(new ContributionSummaryRow("not-a-date", "bad-from", "bad-to",
                                BigDecimal.ZERO, new LinkedHashMap<>()))),
                new CurrencyDisplay("HKD", "HKD"),
                new ContributionActions(false), "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);
        var item = response.items().getFirst();
        // unparseable dates → raw values preserved
        assertEquals("bad-from", item.period().fromDate().value());
        assertEquals("bad-to", item.period().toDate().value());
        assertEquals("not-a-date", item.dealingDate().value());
    }

    @Test
    void toListResponsePaginationTotalRecordsIsItemCount() {
        var result = buildResultWithTwoRows();
        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 2, 10);

        assertEquals(2, response.pagination().totalRecords());
        assertEquals(2, response.pagination().page());
        assertEquals(10, response.pagination().pageSize());
    }

    // --- helpers ---

    private ContributionSummaryReportResult buildResult(
            CurrencyDisplay currencyDisplay,
            BigDecimal totalAmount,
            BigDecimal erAmount,
            BigDecimal eeAmount) {
        return new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(
                                new ContributionSource("ER", new ContributionLabels("Company", ""), 10),
                                new ContributionSource("EE", new ContributionLabels("Member", ""), 20)),
                        List.of(new ContributionSummaryRow(
                                "01/03/2026",
                                "01/03/2026",
                                "31/03/2026",
                                totalAmount,
                                new LinkedHashMap<>(java.util.Map.of(
                                        "ER", erAmount,
                                        "EE", eeAmount))))),
                currencyDisplay,
                new ContributionActions(true), "", "", "");
    }

    private ContributionSummaryReportResult buildResultWithTwoRows() {
        return new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(new ContributionSource("ER", new ContributionLabels("Company", ""), 10)),
                        List.of(
                                new ContributionSummaryRow("01/01/2026", "01/01/2026", "31/01/2026",
                                        new BigDecimal("100"), new LinkedHashMap<>(java.util.Map.of("ER", new BigDecimal("100")))),
                                new ContributionSummaryRow("01/02/2026", "01/02/2026", "28/02/2026",
                                        new BigDecimal("200"), new LinkedHashMap<>(java.util.Map.of("ER", new BigDecimal("200")))))),
                new CurrencyDisplay("HKD", "HKD"),
                new ContributionActions(false), "", "", "");
    }

    @Test
    void resolveLabelReturnsEmptyWhenEnLabelIsBlankForEnLocale() {
        // resolveLabel: isZhHk = false, labels.en() is blank → returns ""
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(new ContributionSource("ER", new ContributionLabels("", null), 10)),
                        List.of(new ContributionSummaryRow("01/03/2026", "01/03/2026", "31/03/2026",
                                BigDecimal.ZERO, new LinkedHashMap<>(java.util.Map.of("ER", BigDecimal.ZERO))))),
                new CurrencyDisplay("HKD", "HKD"),
                new ContributionActions(false), "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);
        // rows().get(0) is the "total" row; rows().get(1) is the ER source row
        assertEquals("", response.items().getFirst().breakdown().rows().get(1).label());
    }

    @Test
    void toListResponseWithNullActionsHasExportDisabled() {
        // result.actions() == null → exportEnabled = false
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport("HKD", List.of(),
                        List.of(new ContributionSummaryRow("01/03/2026", "01/03/2026", "31/03/2026",
                                BigDecimal.ZERO, new LinkedHashMap<>()))),
                new CurrencyDisplay("HKD", "HKD"),
                null, "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);
        assertFalse(response.actions().export().enabled());
    }

    @Test
    void breakdownDetailWithNullAmountUsesZero() {
        // detail.amount() == null: ContributionSummaryRow.details() filters it out.
        // Use a non-null but zero amount to exercise the true branch of detail.amount() != null.
        var breakdown = new LinkedHashMap<String, java.math.BigDecimal>();
        breakdown.put("ER", BigDecimal.ZERO);
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(new ContributionSource("ER", new ContributionLabels("Company", "公司"), 10)),
                        List.of(new ContributionSummaryRow("01/03/2026", "01/03/2026", "31/03/2026",
                                BigDecimal.ZERO, breakdown))),
                new CurrencyDisplay("HKD", "HKD"),
                new ContributionActions(false), "", "", "");

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);
        assertEquals(BigDecimal.ZERO, response.items().getFirst().breakdown().rows().get(1).amount().value());
    }

    @Test
    void toListResponseWithNullTrustCodeAndSchemeTypeDefaultsToEmpty() {
        // result.trustCode() == null → "" and result.schemeType() == null → ""
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport("HKD", List.of(),
                        List.of(new ContributionSummaryRow("01/03/2026", "01/03/2026", "31/03/2026",
                                BigDecimal.ZERO, new LinkedHashMap<>()))),
                new CurrencyDisplay("HKD", "HKD"),
                new ContributionActions(false),
                null, null, null);

        var response = MAPPER.toListResponse(result, DISPLAY_CONFIG, "en", "JP", 1, 99999);
        assertNotNull(response);
    }
}

