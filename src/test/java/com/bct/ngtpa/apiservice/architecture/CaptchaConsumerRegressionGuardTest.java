package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaptchaConsumerRegressionGuardTest {
  private static final Path REGISTRATION =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/StartRegistrationService.java");
  private static final Path USERNAME_RECOVERY =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/StartUsernameRecoveryService.java");
  private static final Path GATE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/service/auth/captcha/RegistrationCaptchaGateService.java");

  @Test
  void registrationConsumesThroughSharedGateWithoutPersistenceBypass() throws Exception {
    String source = Files.readString(REGISTRATION);
    assertThat(source)
        .containsIgnoringWhitespaces(
            "RegistrationCaptchaGateService",
            "registrationCaptchaGateService.consume",
            "lookupPort.searchMember");
    assertThat(source).doesNotContain("markConsumed(", "applyConsumptionAttempt(");
  }

  @Test
  void usernameRecoveryConsumesThroughSharedGateAfterReservationAndBeforeLookup() throws Exception {
    String source = Files.readString(USERNAME_RECOVERY);
    int reserve = indexOfIgnoringWhitespace(source, "idempotencyPort.reserve");
    int consume = indexOfIgnoringWhitespace(source, "captchaGateService.consume");
    int lookup = indexOfIgnoringWhitespace(source, "lookupPort.search");
    assertThat(source).containsIgnoringWhitespaces("RegistrationCaptchaGateService");
    assertThat(reserve).isGreaterThanOrEqualTo(0).isLessThan(consume);
    assertThat(consume).isGreaterThan(reserve).isLessThan(lookup);
    assertThat(source).doesNotContain("markConsumed(", "applyConsumptionAttempt(");
  }

  @Test
  void sharedGateOwnsTheAtomicAttemptAndBoundedConflictReload() throws Exception {
    String source = Files.readString(GATE);
    assertThat(source)
        .containsIgnoringWhitespaces("applyConsumptionAttempt", "reloadAfterConflict");
    assertThat(count(source, "reloadAfterConflict(command)")).isEqualTo(1);
    assertThat(source).doesNotContain("markConsumed(");
  }

  private static int count(String source, String token) {
    int count = 0;
    for (int from = 0; (from = source.indexOf(token, from)) >= 0; from += token.length()) count++;
    return count;
  }
}
