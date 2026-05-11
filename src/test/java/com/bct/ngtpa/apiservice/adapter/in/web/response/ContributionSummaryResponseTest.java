package com.bct.ngtpa.apiservice.adapter.in.web.response;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContributionListResponseTest {

    @Test
    void recordFieldsAreAccessible() {
        var item = new ContributionItemResponse(
                "CONTRIB-2026-03",
                ContributionItemType.CONTRIBUTION,
                new ContributionPeriodResponse(
                        new ContributionDateValueResponse("2026-03-01", "01/03/2026"),
                        new ContributionDateValueResponse("2026-03-31", "31/03/2026")),
                new ContributionDateValueResponse("2026-03-01", "01/03/2026"),
                new ContributionCurrencyValueResponse("HKD", "HKD"),
                new ContributionTotalContributionResponse(
                        new ContributionAmountValueResponse(new BigDecimal("100"), "100")),
                new ContributionBreakdownResponse(List.of(
                        new ContributionBreakdownRowResponse("Total Contributions",
                                new ContributionAmountValueResponse(new BigDecimal("100"), "100")))));
        var response = new ContributionListResponse(
                new ContributionActionsResponse(new ContributionExportActionResponse(true)),
                List.of(item),
                new ContributionPaginationResponse(1, 99999, 1, false));

        assertEquals(1, response.items().size());
        assertEquals("CONTRIB-2026-03", response.items().getFirst().itemId());
        assertEquals(ContributionItemType.CONTRIBUTION, response.items().getFirst().itemType());
        assertEquals("2026-03-01", response.items().getFirst().period().fromDate().value());
        assertEquals("01/03/2026", response.items().getFirst().period().fromDate().text());
        assertTrue(response.actions().export().enabled());
        assertEquals(1, response.pagination().page());
        assertEquals(99999, response.pagination().pageSize());
        assertEquals(1, response.pagination().totalRecords());
        assertFalse(response.pagination().hasNextPage());
    }
}
