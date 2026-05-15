package com.bct.ngtpa.apiservice.application.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PortalAccessContextResolutionExceptionTest {

    @Test
    void isAnApplicationException() {
        var ex = new PortalAccessContextResolutionException("No profile for notifications");

        assertInstanceOf(ApplicationException.class, ex);
    }

    @Test
    void usesStableMemberContextUnavailableErrorCode() {
        // External error code is kept stable to avoid breaking API clients.
        // PortalAccessContextResolutionException maps to the same public error code
        // as MemberContextResolutionException.
        var ex = new PortalAccessContextResolutionException("No profile for notifications");

        assertEquals(ErrorCodes.MEMBER_CONTEXT_UNAVAILABLE, ex.getErrorCode());
    }

    @Test
    void retainsDiagnosticMessage() {
        var ex = new PortalAccessContextResolutionException("No temporary portal access context profile configured for accountRef: contributions");

        assertEquals(
                "No temporary portal access context profile configured for accountRef: contributions",
                ex.getMessage());
    }
}
