package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first behavioral ownership boundary derived from the complete legacy REQUEST matrix. */
class UsernameRecoveryRequestProtectedOperationParityBoundaryTest {
  private static final Path OPERATION =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/UsernameRecoveryRequestProtectedOperation.java");
  private static final Path PARITY =
      Path.of(
          "src/test/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/UsernameRecoveryRequestProtectedOperationTest.java");

  @Test
  void directExecutableParityHarnessExists() {
    assertThat(PARITY).exists();
  }

  @Test
  void parityHarnessCoversPrerequisitesAndDeterministicPreDispatchOutcomes() throws Exception {
    String source = Files.readString(PARITY);
    assertThat(source)
        .contains("new UsernameRecoveryRequestProtectedOperation(")
        .contains("disallowedOrigin", "CsrfMismatch", "sessionAbsent", "SessionExpired")
        .contains("WrongScenario", "invalidSchemeRef", "UnregisteredCandidate")
        .contains("AlreadyRequested", "CandidateNotAvailable", "reservationRevisionConflict")
        .contains("PROVEN_PRE_DISPATCH")
        .doesNotContain("new RequestUsernameRecoveryService(");
  }

  @Test
  void parityHarnessCoversSingleDispatchAndAuthoritativeTransitions() throws Exception {
    String source = Files.readString(PARITY);
    assertThat(source)
        .contains("acceptedDelivery", "definiteDeliveryFailure", "unknownDeliveryOutcome")
        .contains("EmptyDeliveryResult", "ReactiveDeliveryFailure")
        .contains("acceptedPersistenceConflict", "effectiveDestinationFailure")
        .contains("POSSIBLE_SIDE_EFFECT")
        .contains("deliverCalls")
        .contains("isEqualTo(1)")
        .contains("UsernameRecoveryDeliveryState.REQUESTED")
        .contains("UsernameRecoveryDeliveryState.FAILED")
        .contains("UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME");
  }

  @Test
  void parityHarnessCoversPublicProjectionAndSensitiveDiagnosticRedaction() throws Exception {
    String source = Files.readString(PARITY);
    assertThat(source)
        .contains("maskedDestination", "UsernameRecoveryDestinationType.EMAIL")
        .contains("providerMessageId", "workflowCompletion")
        .contains("diagnosticsDoNotExposeProtectedValues")
        .contains("username@example.test", "csrf-secret", "afs-secret", "correlation-secret")
        .contains("doesNotContain");
  }

  @Test
  void operationMustFailClosedOnEmptyDeliveryPublisherAndNotMisclassifyDispatchFailure()
      throws Exception {
    String source = Files.readString(OPERATION).replaceAll("\\s+", "");
    assertThat(source)
        .contains("messageDeliveryPort.deliver(delivery).switchIfEmpty(")
        .contains("persistUnknown(reservedSnapshot,context,command,now)")
        .doesNotContain(
            ".onErrorResume(error->persistFailed(reservedSnapshot,context,command,now))");
  }

  @Test
  void productionRequestRemainsLegacyUntilParityAndSharedReplayAreComplete() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/UsernameRecoveryFeatureConfig.java"));
    assertThat(source)
        .contains("return new IdempotentStartUsernameRecoveryService(")
        .contains("return new IdempotentRequestUsernameRecoveryService(")
        .contains("return new IdempotentCancelUsernameRecoveryService(")
        .doesNotContain("return new RequestUsernameRecoveryService(");
  }
}
