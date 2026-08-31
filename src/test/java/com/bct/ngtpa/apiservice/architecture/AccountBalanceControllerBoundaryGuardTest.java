package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountBalanceControllerBoundaryGuardTest {
  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/accountbalance/AccountBalanceController.java");

  @Test
  void controllerUsesCurrentContextAndHasNoAccountRefOrIdempotencyValidator() throws Exception {
    String source = Files.readString(SOURCE);
    assertThat(source)
        .contains("CurrentPortalAccessContextResolver", "RequestLanguageResolver")
        .containsIgnoringWhitespaces("currentPortalAccessContextResolver.current()");
    assertThat(source)
        .doesNotContain(
            "PortalAccessContextPort", "@RequestHeader(\"Account-Ref\")", "Idempotency-Key");
  }
}
