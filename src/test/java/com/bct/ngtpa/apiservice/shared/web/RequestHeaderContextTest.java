package com.bct.ngtpa.apiservice.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RequestHeaderContextTest {

    @Test
    void normalizesBlankAccountRefToNull() {
        var context = new RequestHeaderContext("   ", "req-123", "zh-HK");

        assertNull(context.accountRef());
    }

    @Test
    void normalizesBlankAcceptLanguageToEnglish() {
        var context = new RequestHeaderContext("ACC-001", "req-123", "   ");

        assertEquals("en", context.language());
    }

    @Test
    void remainsAnImmutableRecordCarrier() {
        var context = new RequestHeaderContext("ACC-001", "req-123", "zh-HK");

        assertTrue(context.getClass().isRecord());
        assertEquals("ACC-001", context.accountRef());
        assertEquals("req-123", context.requestId());
        assertEquals("zh-HK", context.language());
    }
}