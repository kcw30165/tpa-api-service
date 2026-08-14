package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Passing characterization of a known legacy defect. Stage 03 owns correction.
 * This test must be replaced when stale encrypted-payload retry is removed.
 */
class ApimLegacyStalePayloadCharacterizationTest {
    private static final Path FACADE = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacade.java");

    @Test
    void invalidPublicKeyWithoutEncryptionFunctionRetriesTheSamePayloadAsLegacyDefect() throws Exception {
        String source = normalized(Files.readString(FACADE));
        assertThat(source).contains(
                "if (retryContext.encryptionFunction != null)",
                "Object newRequest = retryContext.encryptionFunction.apply(profile, newPk)",
                "Fallback: retry the same payload once",
                "return sendWithProfile(profile, path, requestBody, retryContext)");
    }

    @Test
    void successfulAttemptPublicKeyIsNotExposedByCurrentFacadeContract() throws Exception {
        String source = normalized(Files.readString(FACADE));
        assertThat(source).contains("public Mono<String> postEncrypted(");
        assertThat(source).doesNotContain("record SuccessfulAttempt", "successfulAttemptPublicKey");
    }

    private String normalized(String value) {
        return value.replaceAll("\\s+", " ");
    }
}
