package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the approved Username Recovery workflow error-key vocabulary.
 *
 * <p>Every constant traces to an approved design source:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 8
 *       (authoritative error matrix);
 *   <li>{@code docs/design/6-username-recovery/username-recovery-apim-integration-design.md}
 *       section 8 (downstream mapping matrix).
 * </ul>
 */
class UsernameRecoveryErrorKeyTest {

  @Test
  void implementsWorkflowErrorKeyContract() {
    assertThat(UsernameRecoveryErrorKey.VERIFICATION_FAILED).isInstanceOf(WorkflowErrorKey.class);
  }

  @Test
  void exposesExactlyTheApprovedFeatureConstants() {
    assertThat(UsernameRecoveryErrorKey.values())
        .extracting(UsernameRecoveryErrorKey::name)
        .containsExactlyInAnyOrder(
            "AUTH_FLOW_INVALID",
            "CSRF_INVALID",
            "REGISTRATION_REQUIRED",
            "REQUEST_ALREADY_REQUESTED",
            "SCHEME_REF_INVALID",
            "VERIFICATION_FAILED",
            "WORKFLOW_COMPLETED");
  }

  @Test
  void exposesApprovedPublicCodeStrings() {
    // UR-REQ-001: auth-flow invalid (missing/invalid/expired session)
    assertThat(UsernameRecoveryErrorKey.AUTH_FLOW_INVALID.value())
        .isEqualTo("err.usernamerecovery.authFlow.invalid");
    // UR-REQ-002 and UR-CANCEL-002: CSRF/security invalid
    assertThat(UsernameRecoveryErrorKey.CSRF_INVALID.value())
        .isEqualTo("err.usernamerecovery.csrf.invalid");
    // UR-REQ-004: unregistered candidate, registration required
    assertThat(UsernameRecoveryErrorKey.REGISTRATION_REQUIRED.value())
        .isEqualTo("err.usernamerecovery.registration.required");
    // UR-REQ-008: different key after REQUESTED, already requested
    assertThat(UsernameRecoveryErrorKey.REQUEST_ALREADY_REQUESTED.value())
        .isEqualTo("err.usernamerecovery.request.alreadyRequested");
    // UR-REQ-003: invalid Scheme-Ref
    assertThat(UsernameRecoveryErrorKey.SCHEME_REF_INVALID.value())
        .isEqualTo("err.usernamerecovery.schemeRef.invalid");
    // UR-START-010, APIM-UR-002, APIM-UR-003: member not found
    assertThat(UsernameRecoveryErrorKey.VERIFICATION_FAILED.value())
        .isEqualTo("err.usernamerecovery.verification.failed");
    // UR-REQ-009: new request against COMPLETED, workflow completed
    assertThat(UsernameRecoveryErrorKey.WORKFLOW_COMPLETED.value())
        .isEqualTo("err.usernamerecovery.workflow.completed");
  }

  @Test
  void publicCodeStringsAreUnique() {
    List<String> values =
        Arrays.stream(UsernameRecoveryErrorKey.values())
            .map(UsernameRecoveryErrorKey::value)
            .toList();
    assertThat(values).doesNotHaveDuplicates();
  }

  @Test
  void everyConstantUsesTheApprovedFeaturePrefix() {
    assertThat(UsernameRecoveryErrorKey.values())
        .allSatisfy(key -> assertThat(key.value()).startsWith("err.usernamerecovery."));
  }

  @Test
  void constantsSatisfyRepositoryKeyValidation() {
    for (UsernameRecoveryErrorKey key : UsernameRecoveryErrorKey.values()) {
      assertThatCode(() -> WorkflowErrorKey.of(key.value())).doesNotThrowAnyException();
    }
  }

  @Test
  void constantsDoNotDuplicateSharedOrOtherFeatureKeys() {
    List<String> valuesOwnedElsewhere =
        List.of(
            "err.workflow.idempotency.missing",
            "err.workflow.idempotency.inProgress",
            "err.workflow.idempotency.keyReused",
            "err.workflow.downstream.businessRejected",
            "err.workflow.downstream.unavailable",
            "err.workflow.system.unavailable",
            "err.system.unavailable",
            "err.security.authentication.required",
            "err.request.invalid",
            "err.request.validation.failed");

    List<String> usernameRecoveryValues =
        Arrays.stream(UsernameRecoveryErrorKey.values())
            .map(UsernameRecoveryErrorKey::value)
            .toList();

    assertThat(usernameRecoveryValues).doesNotContainAnyElementsOf(valuesOwnedElsewhere);
  }
}
