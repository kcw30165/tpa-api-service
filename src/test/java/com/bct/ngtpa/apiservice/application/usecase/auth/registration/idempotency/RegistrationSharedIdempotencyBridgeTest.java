package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationSharedIdempotencyBridgeTest {
  @Test
  void mapsAllFourActionsToStableSharedIdentityWithoutExposingRawScope() {
    var factory = new RegistrationSharedIdempotencyIdentityFactory();
    for (RegistrationSharedIdempotencyAction action :
        RegistrationSharedIdempotencyAction.values()) {
      var operation = factory.create(action, "requestKey01", "auth-flow-session-sensitive-value");
      assertThat(operation.capability().value()).isEqualTo("registration");
      assertThat(operation.action().value()).isEqualTo(action.value());
      assertThat(operation.key().value()).isEqualTo("requestKey01");
      assertThat(operation.scope().value())
          .matches("[0-9a-f]{64}")
          .doesNotContain("auth-flow-session-sensitive-value");
    }
  }

  @Test
  void fingerprintIsDeterministicVersionedOrderSensitiveAndActionSeparated() {
    var factory = new RegistrationSharedFingerprintFactory();
    var first = factory.create(RegistrationSharedIdempotencyAction.START, List.of("A", "B"));
    assertThat(first.version()).isEqualTo(1);
    assertThat(first.value()).matches("[0-9a-f]{64}");
    assertThat(first)
        .isEqualTo(factory.create(RegistrationSharedIdempotencyAction.START, List.of("A", "B")));
    assertThat(first)
        .isNotEqualTo(factory.create(RegistrationSharedIdempotencyAction.START, List.of("B", "A")));
    assertThat(first)
        .isNotEqualTo(
            factory.create(RegistrationSharedIdempotencyAction.OTP_RESEND, List.of("A", "B")));
  }

  @Test
  void identityRejectsMissingKeyOrScopeWithoutDisclosingValues() {
    var factory = new RegistrationSharedIdempotencyIdentityFactory();
    assertThatThrownBy(() -> factory.create(RegistrationSharedIdempotencyAction.START, "", "scope"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> factory.create(RegistrationSharedIdempotencyAction.START, "key00001", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void classifierFactoriesExposeOnlyExplicitSafeOrConservativeChoices() {
    assertThat(
            RegistrationSharedFailureClassifiers.provenPreDispatch()
                .classify(new RuntimeException()))
        .isEqualTo(IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT);
    assertThat(
            RegistrationSharedFailureClassifiers.possibleSideEffect()
                .classify(new RuntimeException()))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
  }
}
