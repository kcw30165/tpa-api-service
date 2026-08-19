package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationAuthFlowContextArchitectureGuardTest {

  @Test
  void applicationContextModelsHaveNoSpringRedisApimOrWebDependencies() throws Exception {
    Path root =
        Path.of("src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/registration/context");
    if (!Files.exists(root)) {
      return;
    }
    try (var files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file);
        assertThat(source)
            .doesNotContain(
                "org.springframework",
                "jakarta.servlet",
                "adapter.",
                "lettuce",
                "redis",
                "org.slf4j");
      }
    }
  }
}
