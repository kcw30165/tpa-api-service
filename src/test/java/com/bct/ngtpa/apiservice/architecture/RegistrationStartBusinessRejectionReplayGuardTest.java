package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationStartBusinessRejectionReplayGuardTest {
  @Test
  void deterministicRejectionIsPersistedAndReplayedWithItsPublicError() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/StartRegistrationService.java"));
    assertThat(source)
        .contains("markCompleted(command, hash, verificationFailure)")
        .contains("jsonField(\"status\"")
        .contains("jsonField(\"errorCode\"")
        .contains("jsonField(\"errorTarget\"")
        .contains("RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY");
  }
}
