package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class P6RegistrationStartSecurityBoundaryGuardTest {
  @Test
  void p6RegistrationStartDoesNotHardcodeUserFacingText() throws IOException {
    String text = productionText();
    assertThat(text)
        .doesNotContain(
            "We are unable to verify your details",
            "A new verification code has been sent",
            "Registration completed successfully",
            "我們未能核實您的資料");
  }

  private static String productionText() throws IOException {
    Path root = Path.of("src/main/java/com/bct/ngtpa/apiservice");
    if (!Files.exists(root)) return "";
    StringBuilder builder = new StringBuilder();
    try (var paths = Files.walk(root)) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(path -> builder.append(read(path)).append('\n'));
    }
    return builder.toString();
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException exception) {
      return "";
    }
  }
}
