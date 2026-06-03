package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PageConfigEnumCoverageTest {

    @Test
    void exposesWhenOperatorValues() {
        assertArrayEquals(new WhenOperator[] {
                WhenOperator.notBlank,
                WhenOperator.blank,
                WhenOperator.changed,
                WhenOperator.all,
                WhenOperator.any,
                WhenOperator.allGroupsEmpty
        }, WhenOperator.values());
        assertEquals(WhenOperator.changed, WhenOperator.valueOf("changed"));
    }

    @Test
    void exposesThenOperatorValues() {
        assertArrayEquals(new ThenOperator[] {
                ThenOperator.fail,
                ThenOperator.required,
                ThenOperator.allRequired,
                ThenOperator.showMessage
        }, ThenOperator.values());
        assertEquals(ThenOperator.showMessage, ThenOperator.valueOf("showMessage"));
    }

    @Test
    void exposesSeverityValues() {
        assertArrayEquals(new Severity[] {
                Severity.error,
                Severity.warning,
                Severity.info
        }, Severity.values());
        assertEquals(Severity.warning, Severity.valueOf("warning"));
    }
}
