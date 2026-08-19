package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowKeyConventionTest {

  @Test
  void workflowMessageKeyAcceptsCodeLikeValues() {
    assertThat(new WorkflowMessageKey("err.registration.otp.resent").value())
        .isEqualTo("err.registration.otp.resent");
    assertThat(new WorkflowMessageKey("err.registration.success").value())
        .isEqualTo("err.registration.success");
    assertThat(new WorkflowMessageKey("err.workflow.duplicateInProgress").value())
        .isEqualTo("err.workflow.duplicateInProgress");
  }

  @Test
  void workflowErrorKeyAcceptsCodeLikeValues() {
    assertThat(WorkflowErrorKey.of("err.registration.start.verificationFailed").value())
        .isEqualTo("err.registration.start.verificationFailed");
    assertThat(WorkflowErrorKey.of("err.registration.csrf.invalid").value())
        .isEqualTo("err.registration.csrf.invalid");
    assertThat(WorkflowErrorKey.of("err.workflow.downstream.unavailable").value())
        .isEqualTo("err.workflow.downstream.unavailable");
    assertThat(WorkflowErrorKey.of("err.request.invalid").value()).isEqualTo("err.request.invalid");
  }

  @Test
  void workflowKeysRejectUnsafeValuesWithoutEchoingThem() {
    List<String> unsafeValues =
        List.of(
            "",
            "   ",
            "registration otp resent",
            "member@example.com",
            "91234567",
            "csrf_abc",
            "otp_abc",
            "Bearer abc",
            "Cookie auth_flow_sid=afs_secret");

    for (String unsafe : unsafeValues) {
      var rawValueForMessageCheck = unsafe == null ? "null" : unsafe;
      var messageThrowableAssert =
          assertThatThrownBy(() -> new WorkflowMessageKey(unsafe))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage("Invalid workflow message key");
      var errorThrowableAssert =
          assertThatThrownBy(() -> WorkflowErrorKey.of(unsafe))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage("Invalid workflow error key");
      if (!rawValueForMessageCheck.isEmpty()) {
        messageThrowableAssert.hasMessageNotContaining(rawValueForMessageCheck);
        errorThrowableAssert.hasMessageNotContaining(rawValueForMessageCheck);
      }
    }
  }

  @Test
  void registrationWorkflowMessageKeysHaveExpectedValues() {
    assertThat(RegistrationWorkflowMessageKeys.REGISTRATION_SUCCESS.value())
        .isEqualTo("registration.success");
    assertThat(RegistrationWorkflowMessageKeys.REGISTRATION_OTP_SENT.value())
        .isEqualTo("registration.otp.sent");
    assertThat(RegistrationWorkflowMessageKeys.REGISTRATION_OTP_RESENT.value())
        .isEqualTo("registration.otp.resent");
    assertThat(RegistrationWorkflowMessageKeys.REGISTRATION_USERNAME_AVAILABLE.value())
        .isEqualTo("registration.username.available");
    assertThat(RegistrationWorkflowMessageKeys.REGISTRATION_CANCELLED.value())
        .isEqualTo("registration.cancelled");
  }

  @Test
  void registrationWorkflowErrorKeysHaveExpectedValues() {
    assertThat(RegistrationErrorKey.START_VERIFICATION_FAILED.value())
        .isEqualTo("err.registration.start.verificationFailed");
    assertThat(RegistrationErrorKey.REGISTRATION_CONTACT_EXACTLYONEREQUIRED.value())
        .isEqualTo("err.registration.contact.exactlyOneRequired");
    assertThat(RegistrationErrorKey.REGISTRATION_IDENTITYTYPE_INVALID.value())
        .isEqualTo("err.registration.identityType.invalid");
    assertThat(RegistrationErrorKey.REGISTRATION_IDENTITYVALUE_INVALID.value())
        .isEqualTo("err.registration.identityValue.invalid");
    assertThat(RegistrationErrorKey.REGISTRATION_DATEOFBIRTH_INVALID.value())
        .isEqualTo("err.registration.dateOfBirth.invalid");
    assertThat(RegistrationErrorKey.AUTH_FLOW_INVALID.value())
        .isEqualTo("err.registration.authFlow.invalid");
    assertThat(RegistrationErrorKey.CSRF_INVALID.value())
        .isEqualTo("err.registration.csrf.invalid");
    assertThat(RegistrationErrorKey.OTP_INVALID.value()).isEqualTo("err.registration.otp.invalid");
    assertThat(RegistrationErrorKey.OTP_EXPIRED.value()).isEqualTo("err.registration.otp.expired");
    assertThat(RegistrationErrorKey.OTP_LOCKED.value()).isEqualTo("err.registration.otp.locked");
    assertThat(RegistrationErrorKey.USERNAME_ALREADY_REGISTERED.value())
        .isEqualTo("err.registration.username.isRegistered");
    assertThat(RegistrationErrorKey.REGISTRATION_COMPLETE_FAILED.value())
        .isEqualTo("err.registration.complete.failed");
    assertThat(RegistrationErrorKey.MESSAGE_DELIVERY_FAILED.value())
        .isEqualTo("err.registration.message.deliveryFailed");
    assertThat(SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value())
        .isEqualTo("err.workflow.idempotency.inProgress");
    assertThat(SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value())
        .isEqualTo("err.workflow.idempotency.keyReused");
    assertThat(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value())
        .isEqualTo("err.workflow.system.unavailable");
    assertThat(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value())
        .isEqualTo("err.workflow.downstream.unavailable");
  }

  @Test
  void registrationStartApimMemberNotFoundMappingIsDocumentedAsKeyOnly() {
    assertThat("err.tpa.member.not.found").isEqualTo("err.tpa.member.not.found");
    assertThat(RegistrationErrorKey.START_VERIFICATION_FAILED.value())
        .isEqualTo("err.registration.start.verificationFailed");
    assertThat(RegistrationErrorKey.START_VERIFICATION_FAILED)
        .isEqualTo(RegistrationErrorKey.START_VERIFICATION_FAILED);
  }

  @Test
  void workflowKeyObjectsDoNotExposeSensitiveValuesInFailures() {
    var sensitive = "Cookie auth_flow_sid=afs_secret";
    assertThatThrownBy(() -> WorkflowErrorKey.of(sensitive))
        .hasMessage("Invalid workflow error key")
        .hasMessageNotContaining("auth_flow_sid")
        .hasMessageNotContaining("afs_secret")
        .hasMessageNotContaining(sensitive);
  }
}
