package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionWebDisplayConfig;
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

class ContributionSummaryWebMapperTest {

    private final ContributionSummaryWebMapper mapper = new ContributionSummaryWebMapper();

    private static final ContributionWebDisplayConfig DISPLAY_CONFIG = new ContributionWebDisplayConfig(
            "Total Contributions",
            "供款總額",
            "Dealing date處理日期",
            "Contribution Periods供款期",
            "Total Contributions供款總額");

    @Test
    void toResponseFormatsNullAmountAsZeroWhenCurrencyDisplayIsNull() {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(new ContributionSource("ER", new ContributionLabels("Company", ""), 10)),
                        List.of(new ContributionSummaryRow(
                                "01/03/2026",
                                "01/03/2026",
                                "31/03/2026",
                                null,
                                new LinkedHashMap<>()))),
                null);

        var response = mapper.toResponse(result, DISPLAY_CONFIG);

        assertEquals("0", response.contributions().getFirst().totalContributionEn());
        assertEquals("0", response.contributions().getFirst().totalContributionZh());
        assertEquals("0", response.contributions().getFirst().details().getFirst().amountEn());
        assertEquals("0", response.contributions().getFirst().details().getFirst().amountZh());
    }

    @Test
    void toResponseOmitsCurrencyPrefixWhenCurrencyDisplayIsBlank() {
        var result = new ContributionSummaryReportResult(
                new ContributionSummaryReport(
                        "HKD",
                        List.of(),
                        List.of(new ContributionSummaryRow(
                                "01/03/2026",
                                "01/03/2026",
                                "31/03/2026",
                                new BigDecimal("100.50"),
                                new LinkedHashMap<>()))),
                new CurrencyDisplay("", ""));

        var response = mapper.toResponse(result, DISPLAY_CONFIG);

        assertEquals("100.5", response.contributions().getFirst().totalContributionEn());
        assertEquals("100.5", response.contributions().getFirst().totalContributionZh());
    }

    @Test
    void toResponseIncludesCurrencyPrefixWhenAvailable() {
        var result = buildResultWithSources(
                new CurrencyDisplay("HKD", "港元"),
                new BigDecimal("24908.45"),
                new BigDecimal("17791.75"),
                new BigDecimal("7116.7"));

        var response = mapper.toResponse(result, DISPLAY_CONFIG);
        var item = response.contributions().getFirst();

        assertEquals("HKD 24908.45", item.totalContributionEn());
        assertEquals("港元 24908.45", item.totalContributionZh());
    }

    @Test
    void toResponseInsertsTotalContributionDetailRowFirst() {
        var result = buildResultWithSources(
                new CurrencyDisplay("HKD", "港元"),
                new BigDecimal("24908.45"),
                new BigDecimal("17791.75"),
                new BigDecimal("7116.7"));

        var response = mapper.toResponse(result, DISPLAY_CONFIG);
        var firstDetail = response.contributions().getFirst().details().getFirst();

        assertEquals("Total Contributions", firstDetail.labels().en());
        assertEquals("供款總額", firstDetail.labels().zh());
        assertEquals("HKD 24908.45", firstDetail.amountEn());
        assertEquals("港元 24908.45", firstDetail.amountZh());
    }

    @Test
    void toResponsePreservesSourceOrderInDetails() {
        var result = buildResultWithSources(
                new CurrencyDisplay("HKD", "港元"),
                new BigDecimal("24908.45"),
                new BigDecimal("17791.75"),
                new BigDecimal("7116.7"));

        var response = mapper.toResponse(result, DISPLAY_CONFIG);
        var details = response.contributions().getFirst().details();

        assertEquals(3, details.size());
        assertEquals("Company", details.get(1).labels().en());
        assertEquals("HKD 17791.75", details.get(1).amountEn());
        assertEquals("Member", details.get(2).labels().en());
        assertEquals("HKD 7116.7", details.get(2).amountEn());
    }

    @Test
    void toResponseUsesTotalLabelsFromDisplayConfig() {
        var result = buildResultWithSources(
                new CurrencyDisplay("HKD", "港元"),
                new BigDecimal("100"),
                new BigDecimal("60"),
                new BigDecimal("40"));

        var customConfig = new ContributionWebDisplayConfig(
                "My Total En",
                "My Total Zh",
                "header",
                "period",
                "total");

        var response = mapper.toResponse(result, customConfig);
        var totalDetail = response.contributions().getFirst().details().getFirst();

        assertEquals("My Total En", totalDetail.labels().en());
        assertEquals("My Total Zh", totalDetail.labels().zh());
    }

    @Test
    void formatAmountStripsTrailingZerosFromDecimal() {
        assertEquals("100", mapper.formatAmount("", new BigDecimal("100.00")));
        assertEquals("100.5", mapper.formatAmount("", new BigDecimal("100.50")));
        assertEquals("24908.45", mapper.formatAmount("", new BigDecimal("24908.45")));
    }

    @Test
    void formatAmountReturnsZeroForNullAmount() {
        assertEquals("0", mapper.formatAmount("", null));
    }

    @Test
    void toLabelsResponseMapsEnAndZh() {
        var labels = new ContributionLabels("English", "中文");
        var response = mapper.toLabelsResponse(labels);

        assertEquals("English", response.en());
        assertEquals("中文", response.zh());
    }

    private ContributionSummaryReportResult buildResultWithSources(
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
                currencyDisplay);
    }
}
