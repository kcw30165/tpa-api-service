package com.bct.ngtpa.apiservice.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApimExceptionDefaultCodeTest {

    @Test
    void unexpectedApimStatusDefaultsToApimUnexpectedInsteadOfSystemUnexpected() {
        ApimException exception = new ApimException(HttpStatus.I_AM_A_TEAPOT, "Unexpected APIM status");

        assertEquals("err.apim.unexpected", exception.getErrorCode());
    }
}
