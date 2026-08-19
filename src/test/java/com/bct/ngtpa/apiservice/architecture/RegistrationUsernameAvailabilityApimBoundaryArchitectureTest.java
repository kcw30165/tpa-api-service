package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RegistrationUsernameAvailabilityApimBoundaryArchitectureTest {
  private static final List<String> DISALLOWED_APPLICATION_IMPORTS =
      List.of(
          "import org.springframework.",
          "import org.springframework.web.",
          "import com.bct.ngtpa.apiservice.adapter.out.apim.");

  @Test
  void applicationUsernameAvailabilityPortAndDtosDoNotImportSpringWebOrApimAdapterTypes()
      throws IOException {
    try (Stream<Path> files =
        Stream.of(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/RegistrationUsernameAvailabilityPort.java"),
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/RegistrationUsernameAvailabilityCommand.java"),
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/RegistrationUsernameAvailabilityResult.java"),
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/RegistrationUsernameAvailabilityStatus.java"))) {
      files.forEach(path -> assertNoDisallowedImports(path, DISALLOWED_APPLICATION_IMPORTS));
    }
  }

  @Test
  void apimUsernameAvailabilityAdapterDoesNotHardcodeFrontendResponseCodes() throws IOException {
    Path adapter =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationUsernameAvailabilityAdapter.java");
    if (!Files.exists(adapter)) {
      throw new AssertionError("Expected adapter file to exist: " + adapter);
    }
    String source = Files.readString(adapter);
    assertFalse(
        source.contains("registration.username.isregistered"),
        "APIM adapter must not map raw err-message to FE response code; P9.0 classifier owns that mapping");
    assertFalse(
        source.contains("downstream.generic.businessRejected"),
        "APIM adapter must not apply page/business fallback for technical failures");
  }

  private void assertNoDisallowedImports(Path path, List<String> disallowedImports) {
    try {
      String source = Files.readString(path);
      for (String disallowedImport : disallowedImports) {
        assertFalse(
            source.contains(disallowedImport), path + " must not contain " + disallowedImport);
      }
    } catch (IOException exception) {
      throw new AssertionError("Unable to inspect " + path, exception);
    }
  }
}
