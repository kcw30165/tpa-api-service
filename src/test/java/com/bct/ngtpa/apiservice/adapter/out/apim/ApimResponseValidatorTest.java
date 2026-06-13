package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApimResponseValidatorTest {

    @Test
    void requiresResponsePayload() {
        ApimException exception = assertThrows(ApimException.class,
                () -> ApimResponseValidator.requireValidData(ApimEnvelopeFixtures.envelope(null, null)));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void requiresErrMessageField() {
        ApimException exception = assertThrows(ApimException.class,
                () -> ApimResponseValidator.requireValidData(ApimEnvelopeFixtures.missingErrMessage(List.of())));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void allowsEmptyDataArrayWhenErrMessageIsExactlyEmptyString() {
        List<String> data = List.of();

        assertSame(data, ApimResponseValidator.requireValidData(ApimEnvelopeFixtures.success(data)));
    }

    @Test
    void requiresDataArrayWhenErrMessageIsExactlyEmptyString() {
        ApimException exception = assertThrows(ApimException.class,
                () -> ApimResponseValidator.requireValidData(ApimEnvelopeFixtures.missingDataSuccess()));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void nonEmptyErrMessageThrowsAndDoesNotRequireDataArray() {
        ApimException exception = assertThrows(ApimException.class,
                () -> ApimResponseValidator.requireValidData(ApimEnvelopeFixtures.apimError("APIM failed")));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
        assertEquals("APIM failed", exception.getMessage());
    }

}

