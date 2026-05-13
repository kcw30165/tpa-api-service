package com.bct.ngtpa.apiservice.application.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationExceptionTest {

    @Test
    void retainsErrorCode() {
        var exception = new ApplicationException("err.request.invalid", "Diagnostic message");

        assertEquals("err.request.invalid", exception.getErrorCode());
    }

    @Test
    void retainsCause() {
        var cause = new IllegalStateException("boom");

        var exception = new ApplicationException("err.system.unexpected", "Diagnostic message", cause);

        assertSame(cause, exception.getCause());
    }

    @Test
    void rejectsNullErrorCode() {
        assertThrows(NullPointerException.class, () -> new ApplicationException(null, "Diagnostic message"));
    }

    @Test
    void rejectsBlankErrorCode() {
        assertThrows(IllegalArgumentException.class, () -> new ApplicationException("   ", "Diagnostic message"));
    }
}