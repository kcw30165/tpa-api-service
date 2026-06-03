package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContributionSummaryLegacyResponseRecordsTest {

    @Test
    void exposesContributionSummaryRecordComponents() {
        ContributionSummaryLabelsResponse labels = new ContributionSummaryLabelsResponse("Company", "公司");
        ContributionSummaryDetailResponse detail = new ContributionSummaryDetailResponse(labels, "100.00", "100.00");
        ContributionSummaryItemResponse item = new ContributionSummaryItemResponse(
                "31/03/2026", "01/03/2026 - 31/03/2026", "100.00", "100.00", List.of(detail));
        ContributionSummaryResponse response = new ContributionSummaryResponse(List.of(item));

        assertEquals("Company", labels.en());
        assertEquals("公司", labels.zh());
        assertEquals(labels, detail.labels());
        assertEquals("100.00", detail.amountEn());
        assertEquals("100.00", detail.amountZh());
        assertEquals("31/03/2026", item.dealingDate());
        assertEquals("01/03/2026 - 31/03/2026", item.coveringPeriod());
        assertEquals("100.00", item.totalContributionEn());
        assertEquals("100.00", item.totalContributionZh());
        assertEquals(List.of(detail), item.details());
        assertEquals(List.of(item), response.contributions());
        assertTrue(response.toString().contains("ContributionSummaryResponse"));
    }
}
