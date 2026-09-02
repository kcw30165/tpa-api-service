package com.bct.ngtpa.apiservice.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SharedIdempotencyArchitectureTest {
  private static final Path PACKAGE =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/application/idempotency");
  private static final List<String> FORBIDDEN =
      List.of(
          ".auth.registration.",
          ".auth.login.",
          ".auth.forgotpassword.",
          ".auth.usernamerecovery.",
          ".dto.session.",
          ".adapter.in.",
          ".adapter.out.",
          "org.springframework.",
          ".redis.");

  @Test
  void sharedContractsHaveNoFeatureSessionFrameworkOrPersistenceDependency() throws Exception {
    try (var files = Files.list(PACKAGE)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        assertNoForbiddenImport(Files.readString(file), file.toString());
      }
    }
  }

  @Test
  void guardDetectsARealForbiddenImport() {
    String fixture =
        "package example;\nimport com.bct.ngtpa.apiservice.application.dto.auth.login.LoginDto;\n";
    assertThat(findForbiddenImports(fixture)).containsExactly(".auth.login.");
  }

  private static void assertNoForbiddenImport(String source, String description) {
    assertThat(findForbiddenImports(source)).as(description).isEmpty();
  }

  private static List<String> findForbiddenImports(String source) {
    List<String> imports =
        source.lines().map(String::trim).filter(line -> line.startsWith("import ")).toList();
    return FORBIDDEN.stream()
        .filter(fragment -> imports.stream().anyMatch(line -> line.contains(fragment)))
        .toList();
  }
}
