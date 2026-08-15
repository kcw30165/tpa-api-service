package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Current execution ownership, retry, crypto-field, and idempotency boundary inventory. */
class ApimExecutionBoundaryCharacterizationTest {
    private static final Path APIM = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim");

    @Test
    void facadeSeparatesTokenCertificateAndBusinessRecoveryAndResolvesProfileBeforeExecution() throws Exception {
        String source = normalized(read("ApimWebClientFacade.java"));
        assertThat(source).contains("return resolveProfile(scope, resolutionContext)",
                "invalid_token", "invalid_public_key", "tokenRetry", "certificateRetry",
                "retryEligibility.retryEligible()", "ApimRetryEligibility.NON_RETRYABLE");
    }

    @Test
    void encryptionDisabledFeaturePathsStillCallProfileAwareFacade() throws Exception {
        for (String file : List.of("ApimContributionSummaryAdapter.java", "ApimUpdatePersonalInformationAdapter.java")) {
            String source = normalized(read(file));
            assertThat(source).as(file).contains("getEncryption().isEnabled()", "apimWebClientFacade.post(");
        }
    }

    @Test
    void featureOwnedCertificateAndEncryptionDuplicationIsMeasurable() throws Exception {
        for (String file : List.of("ApimContributionSummaryAdapter.java", "ApimMemberInfoAdapter.java",
                "ApimReferenceDateRefreshAdapter.java", "ApimUpdatePersonalInformationAdapter.java")) {
            String source = normalized(read(file));
            assertThat(source).as(file).contains("ApimCertificateService", "resolveProfile(",
                    "getPublicKey(", "encryptRequest(", "decryptResponseEnvelope(");
        }
    }

    @Test
    void cryptoFieldSelectionReceivesApiNameAndHasDedicatedCurrentTests() throws Exception {
        String crypto = normalized(read("ApimPayloadCryptoService.java"));
        assertThat(crypto).contains("encryptRequest", "decryptResponseEnvelope", "apiName");
        assertThat(Path.of("src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimPayloadCryptoServiceFieldSelectionTest.java"))
                .exists();
    }

    @Test
    void mutationRetryClassificationIsTransportSideOfApplicationIdempotency() throws Exception {
        assertThat(read("ApimNotificationReadStatusAdapter.java")).contains("IDEMPOTENT_MUTATION");
        assertThat(read("ApimUpdatePersonalInformationAdapter.java")).contains("IDEMPOTENT_MUTATION");
        assertThat(read("forgotpassword/ApimForgotPasswordCompletionAdapter.java")).contains("IDEMPOTENT_MUTATION");
        assertThat(read("registration/ApimRegistrationCompletionAdapter.java")).contains("IDEMPOTENT_MUTATION");
        assertThat(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/port/out/ForgotPasswordIdempotencyPort.java"))
                .exists();
        assertThat(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/port/out/RegistrationIdempotencyPort.java"))
                .exists();
    }

    private String read(String file) throws Exception { return Files.readString(APIM.resolve(file)); }
    private String normalized(String value) { return value.replaceAll("\\s+", " "); }
}
