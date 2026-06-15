package com.bct.ngtpa.apiservice.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApimExceptionAdditionalCoverageTest {

    @Test
    void mapsDefaultErrorCodesByHttpStatus() {
        assertEquals(ErrorCodes.APIM_UPSTREAM_FAILURE,
                new ApimException(HttpStatus.BAD_GATEWAY, "bad gateway").getErrorCode());
        assertEquals(ErrorCodes.APIM_SERVICE_UNAVAILABLE,
                new ApimException(HttpStatus.SERVICE_UNAVAILABLE, "unavailable").getErrorCode());
        assertEquals(ErrorCodes.APIM_TIMEOUT,
                new ApimException(HttpStatus.GATEWAY_TIMEOUT, "timeout").getErrorCode());
        assertEquals(ErrorCodes.APIM_UNEXPECTED,
                new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, "internal").getErrorCode());
    }

    @Test
    void normalizesBlankNullAndNumericCodesToDefaultButPreservesTrimmedBusinessCode() {
        assertEquals(ErrorCodes.APIM_UPSTREAM_FAILURE,
                new ApimException(HttpStatus.BAD_GATEWAY, null, "message").getErrorCode());
        assertEquals(ErrorCodes.APIM_UPSTREAM_FAILURE,
                new ApimException(HttpStatus.BAD_GATEWAY, "   ", "message").getErrorCode());
        assertEquals(ErrorCodes.APIM_UPSTREAM_FAILURE,
                new ApimException(HttpStatus.BAD_GATEWAY, "502", "message").getErrorCode());
        assertEquals("err.custom",
                new ApimException(HttpStatus.BAD_GATEWAY, " err.custom ", "message").getErrorCode());
    }

    @Test
    void preservesStatusMessageAndCauseAndRejectsNullStatus() {
        RuntimeException cause = new RuntimeException("cause");
        ApimException exception = new ApimException(HttpStatus.BAD_GATEWAY, "message", cause);

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("message", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertThrows(NullPointerException.class, () -> new ApimException(null, "message"));
    }
}
