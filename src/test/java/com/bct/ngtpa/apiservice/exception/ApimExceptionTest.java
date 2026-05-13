package com.bct.ngtpa.apiservice.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ApimExceptionTest {

    @Test
    void normalizesNumericStatusCodeStringToBusinessErrorCode() {
        var exception = new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, "500", "diagnostic");

        assertEquals(ErrorCodes.SYSTEM_UNEXPECTED, exception.getErrorCode());
    }

    @Test
    void preservesExplicitBusinessErrorCode() {
        var exception = new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, "diagnostic");

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void preservesCause() {
        var cause = new IllegalStateException("boom");
        var exception = new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYSTEM_UNEXPECTED, "diagnostic", cause);

        assertSame(cause, exception.getCause());
    }
}