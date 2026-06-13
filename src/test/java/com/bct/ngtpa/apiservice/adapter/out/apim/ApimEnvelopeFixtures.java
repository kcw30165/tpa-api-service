package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import java.util.List;

/**
 * Test-only builders for APIM response envelopes.
 *
 * <p>The production APIM success contract is intentionally explicit:
 * successful APIM responses must have {@code err-message == ""} and a present
 * {@code data} array. Use {@link #success(List)} or {@link #noRecords()} for
 * valid success fixtures. Use {@link #missingDataSuccess()} only when testing
 * invalid APIM schema handling.
 */
final class ApimEnvelopeFixtures {

    private ApimEnvelopeFixtures() {
    }

    static <T> ApimResponseEnvelope<T> success(List<T> data) {
        return envelope("", data);
    }

    static <T> ApimResponseEnvelope<T> noRecords() {
        return success(List.of());
    }

    static <T> ApimResponseEnvelope<T> apimError(String errMessage) {
        return envelope(errMessage, null);
    }

    static <T> ApimResponseEnvelope<T> missingDataSuccess() {
        return envelope("", null);
    }

    static <T> ApimResponseEnvelope<T> missingErrMessage(List<T> data) {
        return envelope(null, data);
    }

    static <T> ApimResponseEnvelope<T> envelope(String errMessage, List<T> data) {
        return new ApimResponseEnvelope<>(new ApimResponseBody<>(errMessage, data));
    }
}

