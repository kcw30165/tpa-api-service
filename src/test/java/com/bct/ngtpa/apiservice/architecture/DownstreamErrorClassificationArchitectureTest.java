package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DownstreamErrorClassificationArchitectureTest {
  private static final List<String> DISALLOWED_IMPORTS =
      List.of(
          "import org.springframework.",
          "import jakarta.validation.",
          "import reactor.",
          "import org.springframework.http.",
          "import org.springframework.web.");

  @Test
  void applicationModelAndPortRemainFrameworkFree() throws IOException {
    try (Stream<Path> files =
        Stream.concat(
            Files.walk(
                Path.of("src/main/java/com/bct/ngtpa/apiservice/application/dto/downstream")),
            Stream.of(
                Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/application/port/out/DownstreamErrorClassificationPort.java")))) {
      files
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(this::assertNoDisallowedImports);
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
