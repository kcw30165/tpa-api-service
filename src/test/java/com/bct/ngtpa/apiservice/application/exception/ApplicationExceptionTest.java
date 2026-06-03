package com.bct.ngtpa.apiservice.application.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ApplicationExceptionTest {

    @Test
    void trimsAndExposesErrorCode() {
        ApplicationException exception = new ApplicationException(" err.test.code ");

        assertEquals("err.test.code", exception.getErrorCode());
    }

    @Test
    void preservesDiagnosticMessageAndCauseAcrossConstructors() {
        RuntimeException cause = new RuntimeException("cause");

        ApplicationException withMessage = new ApplicationException("err.test", "diagnostic");
        ApplicationException withCause = new ApplicationException("err.test", cause);
        ApplicationException withMessageAndCause = new ApplicationException("err.test", "diagnostic", cause);

        assertEquals("diagnostic", withMessage.getMessage());
        assertSame(cause, withCause.getCause());
        assertEquals("diagnostic", withMessageAndCause.getMessage());
        assertSame(cause, withMessageAndCause.getCause());
    }

    @Test
    void rejectsNullOrBlankErrorCode() {
        assertThrows(NullPointerException.class, () -> new ApplicationException(null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationException("   "));
    }
}
