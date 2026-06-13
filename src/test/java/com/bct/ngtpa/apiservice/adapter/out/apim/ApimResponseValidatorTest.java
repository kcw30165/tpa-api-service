package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApimResponseValidatorTest {

    @Test
    void requiresResponsePayload() {
        ApimException exception = assertThrows(ApimException.class,
                () -> ApimResponseValidator.requireValidData(new ApimResponseEnvelope<>(null)));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void requiresErrMessageField() {
        ApimException exception = assertThrows(ApimException.class,
                () -> ApimResponseValidator.requireValidData(envelope(null, List.of())));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void allowsEmptyDataArrayWhenErrMessageIsExactlyEmptyString() {
        List<String> data = List.of();

        assertSame(data, ApimResponseValidator.requireValidData(envelope("", data)));
    }

    @Test
    void requiresDataArrayWhenErrMessageIsExactlyEmptyString() {
        ApimException exception = assertThrows(ApimException.class,
                () -> ApimResponseValidator.requireValidData(envelope("", null)));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void nonEmptyErrMessageThrowsAndDoesNotRequireDataArray() {
        ApimException exception = assertThrows(ApimException.class,
                () -> ApimResponseValidator.requireValidData(envelope("APIM failed", null)));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
        assertEquals("APIM failed", exception.getMessage());
    }

    private static ApimResponseEnvelope<String> envelope(String errMessage, List<String> data) {
        return new ApimResponseEnvelope<>(new ApimResponseBody<>(errMessage, data));
    }
}

