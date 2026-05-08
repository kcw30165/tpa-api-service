package com.bct.ngtpa.apiservice.adapter.in.web.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContributionSummaryResponseTest {

    @Test
    void recordFieldIsAccessible() {
        var item = new ContributionSummaryItemResponse(
                "01/03/2026",
                "01/03/2026 - 31/03/2026",
                "HKD 100",
                "港元 100",
                List.of());
        var response = new ContributionSummaryResponse(List.of(item));

        assertEquals(1, response.contributions().size());
        assertEquals("01/03/2026", response.contributions().getFirst().dealingDate());
    }
}