package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LintResultCoverageTest {

    @Test
    void startsWithoutErrorsAndReportsErrorsAfterAddingThem() {
        LintResult result = new LintResult();

        assertFalse(result.hasErrors());
        assertEquals(List.of(), result.getErrors());

        result.addError("first");
        result.addError("second");

        assertTrue(result.hasErrors());
        assertEquals(List.of("first", "second"), result.getErrors());
    }

    @Test
    void returnsUnmodifiableErrorsView() {
        LintResult result = new LintResult();
        result.addError("readonly");

        assertThrows(UnsupportedOperationException.class, () -> result.getErrors().add("mutation"));
    }
}
