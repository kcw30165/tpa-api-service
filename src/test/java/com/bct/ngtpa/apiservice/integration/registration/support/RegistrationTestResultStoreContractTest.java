package com.bct.ngtpa.apiservice.integration.registration.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationTestResultStoreContractTest {

  @Test
  void evidenceWriterUsesAtomicReplacementAndLockedFileFallback() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/test/java/com/bct/ngtpa/apiservice/integration/registration/support/RegistrationTestResultStore.java"));

    assertThat(source)
        .contains("writeAtomicallyOrToFallback")
        .contains("StandardCopyOption.ATOMIC_MOVE")
        .contains("registration-test-results-")
        .contains("Canonical registration result CSV is locked");
  }
}
