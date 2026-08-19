package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RegistrationUsernameCheckBoundaryArchitectureTest {
  private static final List<String> DISALLOWED_IMPORTS =
      List.of(
          "import org.springframework.",
          "import jakarta.validation.",
          "import org.springframework.web.");

  @Test
  void usernameCheckApplicationBoundaryHasNoSpringOrJakartaImports() throws IOException {
    try (Stream<Path> files =
        Stream.of(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/registration/RegistrationSelectedTrust.java"),
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/session/authflow/RegistrationUsernameCheck.java"),
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/CheckRegistrationUsernameCommand.java"),
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/CheckRegistrationUsernameResult.java"),
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/CheckRegistrationUsernameResultStatus.java"),
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/RegistrationUsernameAvailabilityCommand.java"),
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/RegistrationUsernameAvailabilityResult.java"),
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/RegistrationUsernameAvailabilityStatus.java"),
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/in/CheckRegistrationUsernameUseCase.java"),
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/RegistrationUsernameAvailabilityPort.java"))) {
      files.forEach(this::assertNoDisallowedImports);
    }
  }

  private void assertNoDisallowedImports(Path path) {
    try {
      String source = Files.readString(path);
      for (String disallowedImport : DISALLOWED_IMPORTS) {
        assertFalse(
            source.contains(disallowedImport), path + " must not contain " + disallowedImport);
      }
    } catch (IOException exception) {
      throw new AssertionError("Unable to inspect " + path, exception);
    }
  }
}
