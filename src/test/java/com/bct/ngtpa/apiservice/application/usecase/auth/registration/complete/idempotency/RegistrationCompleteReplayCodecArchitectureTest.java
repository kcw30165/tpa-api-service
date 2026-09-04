package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationCompleteReplayCodecArchitectureTest {
  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/"
              + "idempotency/RegistrationCompleteReplayCodec.java");

  @Test
  void safeSnapshotContainsOnlyPublicCompletionFields() throws Exception {
    String source = Files.readString(SOURCE);
    assertThat(source)
        .contains("RegistrationSharedIdempotencyAction.COMPLETE")
        .contains("result.status().name()")
        .contains("result.nextStep().name()")
        .contains("result.registered()")
        .contains("result.clearAuthFlowCookie()")
        .contains("new TrustWire(trust.accountEnv(), trust.trustCode())")
        .contains("new ErrorWire(")
        .doesNotContain(
            "result.password",
            "confirmPassword",
            "username",
            "identityValue",
            "dateOfBirth",
            "contactValue",
            "authFlowSessionId",
            "csrfToken",
            "idempotencyKey",
            "selectedTrust",
            "correlationId",
            "protectedReplayReference");
  }

  @Test
  void hydrationIsStrictAndRestoresOrderedPublicTrustShape() throws Exception {
    String source = Files.readString(SOURCE);
    assertThat(source)
        .contains("FAIL_ON_UNKNOWN_PROPERTIES")
        .contains("bridge.hydrate(RegistrationSharedIdempotencyAction.COMPLETE, snapshot)")
        .contains("payload.remainingTrusts().stream()")
        .contains("new RegistrationLookupAccount(")
        .contains("\"\", \"\", trust.accountEnv().trim(), trust.trustCode().trim()")
        .contains("Registration Complete replay trust is invalid")
        .contains("Registration Complete replay decoding failed");
  }

  @Test
  void lifecycleOnlyOutcomesCannotBeCapturedAsPublicResults() throws Exception {
    String source = Files.readString(SOURCE);
    assertThat(source)
        .contains("status == CompleteRegistrationResultStatus.COMPLETED_REPLAY")
        .contains("status == CompleteRegistrationResultStatus.UNKNOWN_OUTCOME")
        .contains("Registration Complete lifecycle status is not a capturable public result");
  }

  @Test
  void codecHasNoCompletionWorkflowSideEffects() throws Exception {
    String source = Files.readString(SOURCE);
    assertThat(source)
        .doesNotContain(
            "RegistrationCompletionPort",
            "AuthFlowSessionPort",
            "RegistrationLookupPort",
            "AuthFlowSessionCancellationPort",
            "AuthFlowSessionCompletionLockPort",
            "completion.complete(",
            "searchUnregisteredMember(",
            "sessions.",
            "locks.",
            "cancellation.",
            ".subscribe(",
            ".block(");
  }
}
