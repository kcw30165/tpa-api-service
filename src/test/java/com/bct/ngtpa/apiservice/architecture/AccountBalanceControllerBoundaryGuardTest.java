package com.bct.ngtpa.apiservice.architecture;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
class AccountBalanceControllerBoundaryGuardTest {
  private static final Path SOURCE = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/accountbalance/AccountBalanceController.java");
  @Test void controllerUsesCurrentContextAndHasNoAccountRefOrIdempotencyValidator() throws Exception {
    String source = Files.readString(SOURCE);
    assertThat(source).contains("CurrentPortalAccessContextResolver", "currentPortalAccessContextResolver.current()", "RequestLanguageResolver");
    assertThat(source).doesNotContain("PortalAccessContextPort", "@RequestHeader(\"Account-Ref\")", "Idempotency-Key");
  }
  @Test void subphaseAddsNoDownstreamAdapter() throws Exception {
    assertThat(Files.exists(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/accountbalance"))).isFalse();
  }
}
