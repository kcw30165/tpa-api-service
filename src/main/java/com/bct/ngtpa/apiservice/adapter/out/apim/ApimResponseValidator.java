package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Shared validation for APIM response envelopes.
 *
 * <p>Global contract:
 * <ul>
 *   <li>{@code response} must be present.</li>
 *   <li>{@code err-message} must be present.</li>
 *   <li>{@code err-message == ""} means success and requires {@code data} to be present as an array.</li>
 *   <li>{@code data: []} is valid for a no-record success response.</li>
 *   <li>{@code err-message != ""} means APIM returned an error; {@code data} may be absent.</li>
 * </ul>
 */
public final class ApimResponseValidator {

    private ApimResponseValidator() {
    }

    public static <T> List<T> requireValidData(ApimResponseEnvelope<T> envelope) {
        var payload = envelope == null ? null : envelope.getResponse();
        if (payload == null) {
            throw invalid("APIM response payload is invalid: response is missing.");
        }
        String errMessage = payload.getErrMessage();
        if (errMessage == null) {
            throw invalid("APIM response payload is invalid: response.err-message is missing.");
        }
        if (!errMessage.isEmpty()) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, errMessage);
        }
        List<T> data = payload.getData();
        if (data == null) {
            throw invalid("APIM response payload is invalid: response.data is required when err-message is empty.");
        }
        return data;
    }

    private static ApimException invalid(String message) {
        return new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, message);
    }
}

