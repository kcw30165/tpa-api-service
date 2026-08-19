package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Historical Stage 01 defect guard, inverted in Stage 03 to prove stale body retry is removed. */
class ApimLegacyStalePayloadCharacterizationTest {
  @Test
  void bodyOnlyInvalidPublicKeyPathNoLongerRetriesTheSameEncryptedPayload() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacade.java"));
    assertThat(source).doesNotContain("Fallback: retry the same payload once");
    assertThat(source)
        .contains(
            "A body-only call cannot safely regenerate encrypted ciphertext",
            "Do not retry stale requestBody after certificate invalidation");
  }

  @Test
  void correctedEncryptedPathRefreshesKeyRegeneratesRequestAndCarriesSuccessfulKey()
      throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacade.java"));
    assertThat(source)
        .contains(
            "retryContext.responseVerificationKey = newPk",
            "retryContext.encryptionFunction.apply(profile, newPk)",
            "context.responseVerificationKey");
  }
}
