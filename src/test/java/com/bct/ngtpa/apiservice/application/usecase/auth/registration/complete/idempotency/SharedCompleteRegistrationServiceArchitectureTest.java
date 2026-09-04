package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SharedCompleteRegistrationServiceArchitectureTest {
  private static final Path SERVICE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/"
              + "idempotency/SharedCompleteRegistrationService.java");

  @Test
  void prerequisitesPrecedeSharedExecution() throws Exception {
    String source = Files.readString(SERVICE);
    int context = source.indexOf("contexts.load(");
    int expiry = source.indexOf("expiry.refresh(");
    int username = source.indexOf("usernames.load(session)");
    int password = source.indexOf("passwords.validate(");
    int executor = source.indexOf("executor.execute(execution)");
    assertThat(context).isGreaterThanOrEqualTo(0);
    assertThat(expiry).isGreaterThan(context);
    assertThat(username).isGreaterThan(expiry);
    assertThat(password).isGreaterThan(username);
    assertThat(executor).isGreaterThan(password);
  }

  @Test
  void completeUsesOpaqueSharedIdentityAndPasswordDigestFingerprint() throws Exception {
    String source = Files.readString(SERVICE);
    assertThat(source)
        .contains("RegistrationSharedIdempotencyAction.COMPLETE")
        .contains("command.idempotencyKey().value(), sessionScope")
        .contains("List.of(sessionScope, sha256(command.password()))")
        .doesNotContain("RegistrationIdempotencyPort", "RegistrationRequestHashGenerator");
  }

  @Test
  void classifierPreservesPreDispatchAndAmbiguousBoundary() throws Exception {
    String source = Files.readString(SERVICE);
    assertThat(source)
        .contains("failure instanceof RegistrationCompletionPreDispatchException")
        .contains("IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT")
        .contains("IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT");
  }

  @Test
  void replayReturnsOriginalTypedResultAndLifecycleFailuresRemainCanonical() throws Exception {
    String source = Files.readString(SERVICE);
    assertThat(source)
        .contains("case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response)")
        .contains("case FAILED_EXISTING")
        .contains("case UNKNOWN_OUTCOME_EXISTING")
        .contains("REGISTRATION_COMPLETE_OUTCOMEUNKNOWN")
        .doesNotContain("CompleteRegistrationResultStatus.COMPLETED_REPLAY,");
  }
}
