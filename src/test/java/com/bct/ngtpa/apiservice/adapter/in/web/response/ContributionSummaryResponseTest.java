package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionWebDisplayConfig;
import com.bct.ngtpa.apiservice.application.dto.ContributionSummaryReportResult;
import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;
import com.bct.ngtpa.apiservice.domain.model.ContributionSource;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryReport;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryRow;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContributionSummaryResponseTest {

    @Test
    void fromFormatsNullAmountsAsZeroWithoutCurrencyPrefixWhenCurrencyDisplayMissing() {
        var response = ContributionSummaryResponse.from(
                new ContributionSummaryReportResult(
                        new ContributionSummaryReport(
                                "HKD",
                                List.of(new ContributionSource("ER", new ContributionLabels("Company", ""), 10)),
                                List.of(new ContributionSummaryRow(
                                        "01/03/2026",
                                        "01/03/2026",
                                        "31/03/2026",
                                        null,
                                        new LinkedHashMap<>()))),
                        null),
                new ContributionWebDisplayConfig(
                    "Total Contributions",
                    "供款總額",
                    "Dealing date處理日期",
                    "Contribution Periods供款期",
                    "Total Contributions供款總額"
                ));

        assertEquals("0", response.contributions().getFirst().totalContributionEn());
        assertEquals("0", response.contributions().getFirst().totalContributionZh());
        assertEquals("0", response.contributions().getFirst().details().getFirst().amountEn());
        assertEquals("0", response.contributions().getFirst().details().getFirst().amountZh());
    }
}