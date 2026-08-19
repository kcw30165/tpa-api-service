package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class P32RuntimeRateLimitComponentGuardTest {

  @Test
  void p3_2DoesNotIntroduceRuntimeRateLimitComponents() throws Exception {
    Set<String> sourceFileNames;
    try (var paths = Files.walk(Path.of("src/main/java"))) {
      sourceFileNames =
          paths
              .filter(Files::isRegularFile)
              .map(path -> path.getFileName().toString())
              .collect(Collectors.toSet());
    }

    assertTrue(
        sourceFileNames.stream().noneMatch(name -> name.contains("DestinationHash")),
        "P3.2 must not introduce destination hashing helpers");
  }
}
