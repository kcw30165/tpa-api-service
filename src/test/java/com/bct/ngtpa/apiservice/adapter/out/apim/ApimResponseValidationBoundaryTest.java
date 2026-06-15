package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApimResponseValidationBoundaryTest {

    private static final List<String> APIM_ADAPTERS = List.of(
            "ApimContributionSummaryAdapter.java",
            "ApimMemberInfoAdapter.java",
            "ApimNoticeMessageAdapter.java",
            "ApimNotificationReadStatusAdapter.java",
            "ApimReferenceDataCountriesAdapter.java",
            "ApimUpdatePersonalInformationAdapter.java");

    @Test
    void apimAdaptersUseSharedEnvelopeValidatorInsteadOfLocalErrMessageAndDataValidation() throws IOException {
        for (String adapter : APIM_ADAPTERS) {
            String source = readApimSource(adapter);

            assertTrue(source.contains("ApimResponseValidator.requireSuccessData(response)"),
                    adapter + " must use the shared APIM envelope validator");
            assertFalse(source.contains("payload.getErrMessage()"),
                    adapter + " must not perform local err-message validation");
            assertFalse(source.contains("payload.getData()"),
                    adapter + " must not read APIM envelope data directly after validation migration");
            assertFalse(source.contains("APIM response payload is missing."),
                    adapter + " must not use the legacy missing-payload diagnostic");
        }
    }

    @Test
    void apimUnexpectedIsTheDefaultApimExceptionFallback() throws IOException {
        String errorCodes = readSource("src/main/java/com/bct/ngtpa/apiservice/shared/error/ErrorCodes.java");
        String apimException = readSource("src/main/java/com/bct/ngtpa/apiservice/exception/ApimException.java");

        assertTrue(errorCodes.contains("APIM_UNEXPECTED = \"err.apim.unexpected\""));
        assertTrue(apimException.contains("default -> ErrorCodes.APIM_UNEXPECTED;"));
        assertFalse(apimException.contains("default -> ErrorCodes.SYSTEM_UNEXPECTED;"));
    }

    @Test
    void apimResponseValidatorDocumentsAndEnforcesSuccessDataRequirement() throws IOException {
        String validator = readApimSource("ApimResponseValidator.java");

        assertTrue(validator.contains("err-message == \"\""));
        assertTrue(validator.contains("response.data is required when err-message is empty"));
        assertTrue(validator.contains("return data;"));
    }

    private static String readApimSource(String fileName) throws IOException {
        return readSource("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/" + fileName);
    }

    private static String readSource(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}

