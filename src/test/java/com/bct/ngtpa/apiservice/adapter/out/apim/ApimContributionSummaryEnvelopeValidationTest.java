package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetContributionSummaryDataItem;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApimContributionSummaryEnvelopeValidationTest {

    private final ApimContributionSummaryAdapter adapter = new ApimContributionSummaryAdapter(
            mock(ApimWebClientFacade.class),
            mock(ApimCertificateService.class),
            mock(ApimPayloadCryptoService.class),
            mock(ApimProperties.class));

    @Test
    void throwsApimResponseInvalidWhenSuccessEnvelopeOmitsDataArray() throws Exception {
        ApimException exception = assertThrows(ApimException.class,
                () -> invokeToDataset(ApimEnvelopeFixtures.missingDataSuccess()));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void throwsApimResponseInvalidWhenErrMessageIsMissingEvenIfDataArrayExists() throws Exception {
        ApimException exception = assertThrows(ApimException.class,
                () -> invokeToDataset(ApimEnvelopeFixtures.missingErrMessage(List.of())));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void allowsEmptyDataArrayWhenErrMessageIsExactlyEmptyString() throws Exception {
        ContributionSummaryDataset dataset = assertDoesNotThrow(() -> invokeToDataset(ApimEnvelopeFixtures.noRecords()));

        assertEquals(List.of(), dataset.entries());
        assertEquals(List.of(), dataset.sources());
    }

    @Test
    void nonEmptyErrMessageRemainsApimResponseInvalidAndDoesNotRequireDataArray() throws Exception {
        ApimException exception = assertThrows(ApimException.class,
                () -> invokeToDataset(ApimEnvelopeFixtures.apimError("APIM rejected request")));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
        assertEquals("APIM rejected request", exception.getMessage());
    }

    private ContributionSummaryDataset invokeToDataset(
            ApimResponseEnvelope<GetContributionSummaryDataItem> envelope) throws Exception {
        Method method = ApimContributionSummaryAdapter.class.getDeclaredMethod(
                "toContributionSummaryDataset",
                ApimResponseEnvelope.class);
        method.setAccessible(true);
        try {
            return (ContributionSummaryDataset) method.invoke(adapter, envelope);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

}
