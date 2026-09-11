package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationCompleteProtectedOperationArchitectureTest {
  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/"
              + "idempotency/RegistrationCompleteProtectedOperation.java");

  @Test
  void operationOwnsAcquiredWorkflowAndReactiveLockLifecycle() throws Exception {
    String source = Files.readString(SOURCE);
    String compact = source.replaceAll("\\s+", "");
    assertThat(compact)
        .contains("locks.acquire(command.authFlowSessionId(),completionLockLease)")
        .contains("Mono.usingWhen(")
        .contains("sessions.load(input.command().authFlowSessionId())")
        .contains("AuthFlowStatus.COMPLETING")
        .contains("SelectedTrusttrust=username.selectedTrust()")
        .contains("ActorUserRole.ROLE_USER_MBR")
        .contains("completion.complete(downstream)")
        .contains("searchMember(command)")
        .contains("AuthFlowStatus.TRUST_SELECTION_READY")
        .contains("AuthFlowStatus.COMPLETED")
        .contains("cancellation.cancel(")
        .contains("completionLockReleaseAfterSuccess")
        .contains("completionLockReleaseAfterError")
        .contains("completionLockReleaseAfterCancellation")
        .doesNotContain(".subscribe(", ".block(", ".blockOptional(", "toFuture().get(");
  }

  @Test
  void operationIsIdempotencyPersistenceFrameworkAndAdapterNeutral() throws Exception {
    String source = Files.readString(SOURCE);
    assertThat(source)
        .doesNotContain(
            "RegistrationIdempotencyPort",
            "RegistrationRequestHashGenerator",
            "IdempotencyExecutor",
            "IdempotencyPort",
            "startProcessing(",
            "markCompleted(",
            "markFailed(",
            "markUnknownOutcome(",
            "adapter.out.",
            "org.springframework",
            "jakarta.");
  }

  @Test
  void unexpectedCompletionFailuresRemainPropagatedForSharedClassification() throws Exception {
    String source = Files.readString(SOURCE);
    String dispatch =
        source.substring(source.indexOf("private Mono<CompleteRegistrationResult> dispatch"));
    assertThat(dispatch.replaceAll("\\s+", ""))
        .contains("completion.complete(downstream)")
        .doesNotContain(
            "RegistrationCompletionPreDispatchException.class",
            "CompleteRegistrationResultStatus.UNKNOWN_OUTCOME",
            "CompleteRegistrationResultStatus.SYSTEM_FAILED");
  }
}
