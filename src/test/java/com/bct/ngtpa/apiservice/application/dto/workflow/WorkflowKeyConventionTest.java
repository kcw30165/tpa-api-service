package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowKeyConventionTest {

    @Test
    void workflowMessageKeyAcceptsCodeLikeValues() {
        assertThat(new WorkflowMessageKey("registration.otp.resent").value()).isEqualTo("registration.otp.resent");
        assertThat(new WorkflowMessageKey("registration.success").value()).isEqualTo("registration.success");
        assertThat(new WorkflowMessageKey("workflow.duplicateInProgress").value()).isEqualTo("workflow.duplicateInProgress");
    }

    @Test
    void workflowErrorKeyAcceptsCodeLikeValues() {
        assertThat(new WorkflowErrorKey("registration.start.verificationFailed").value()).isEqualTo("registration.start.verificationFailed");
        assertThat(new WorkflowErrorKey("registration.csrf.invalid").value()).isEqualTo("registration.csrf.invalid");
        assertThat(new WorkflowErrorKey("workflow.downstream.unavailable").value()).isEqualTo("workflow.downstream.unavailable");
        assertThat(new WorkflowErrorKey("err.request.invalid").value()).isEqualTo("err.request.invalid");
    }

    @Test
    void workflowKeysRejectUnsafeValuesWithoutEchoingThem() {
        List<String> unsafeValues = List.of(
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
            var messageThrowableAssert = assertThatThrownBy(() -> new WorkflowMessageKey(unsafe))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid workflow message key");
            var errorThrowableAssert = assertThatThrownBy(() -> new WorkflowErrorKey(unsafe))
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
        assertThat(RegistrationWorkflowMessageKeys.REGISTRATION_SUCCESS.value()).isEqualTo("registration.success");
        assertThat(RegistrationWorkflowMessageKeys.REGISTRATION_OTP_SENT.value()).isEqualTo("registration.otp.sent");
        assertThat(RegistrationWorkflowMessageKeys.REGISTRATION_OTP_RESENT.value()).isEqualTo("registration.otp.resent");
        assertThat(RegistrationWorkflowMessageKeys.REGISTRATION_USERNAME_AVAILABLE.value()).isEqualTo("registration.username.available");
        assertThat(RegistrationWorkflowMessageKeys.REGISTRATION_CANCELLED.value()).isEqualTo("registration.cancelled");
    }

    @Test
    void registrationWorkflowErrorKeysHaveExpectedValues() {
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_START_VERIFICATION_FAILED.value()).isEqualTo("registration.start.verificationFailed");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_CONTACT_EXACTLY_ONE_REQUIRED.value()).isEqualTo("registration.contact.exactlyOneRequired");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_IDENTITY_TYPE_INVALID.value()).isEqualTo("registration.identityType.invalid");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_IDENTITY_VALUE_INVALID.value()).isEqualTo("registration.identityValue.invalid");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_DATE_OF_BIRTH_INVALID.value()).isEqualTo("registration.dateOfBirth.invalid");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_AUTH_FLOW_INVALID.value()).isEqualTo("registration.authFlow.invalid");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_CSRF_INVALID.value()).isEqualTo("registration.csrf.invalid");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_OTP_INVALID.value()).isEqualTo("registration.otp.invalid");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_OTP_EXPIRED.value()).isEqualTo("registration.otp.expired");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_OTP_MAX_ATTEMPTS.value()).isEqualTo("registration.otp.maxAttemptsReached");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_USERNAME_UNAVAILABLE.value()).isEqualTo("registration.username.unavailable");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_COMPLETE_FAILED.value()).isEqualTo("registration.complete.failed");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_MESSAGE_DELIVERY_FAILED.value()).isEqualTo("registration.messageDelivery.failed");
        assertThat(RegistrationWorkflowErrorKeys.WORKFLOW_IDEMPOTENCY_DUPLICATE_IN_PROGRESS.value()).isEqualTo("workflow.idempotency.duplicateInProgress");
        assertThat(RegistrationWorkflowErrorKeys.WORKFLOW_IDEMPOTENCY_KEY_REUSED.value()).isEqualTo("workflow.idempotency.keyReused");
        assertThat(RegistrationWorkflowErrorKeys.WORKFLOW_SYSTEM_UNAVAILABLE.value()).isEqualTo("workflow.system.unavailable");
        assertThat(RegistrationWorkflowErrorKeys.WORKFLOW_DOWNSTREAM_UNAVAILABLE.value()).isEqualTo("workflow.downstream.unavailable");
    }

    @Test
    void registrationStartApimMemberNotFoundMappingIsDocumentedAsKeyOnly() {
        assertThat(RegistrationWorkflowErrorKeys.APIM_MEMBER_NOT_FOUND).isEqualTo("err.tpa.member.not.found");
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_START_VERIFICATION_FAILED.value())
                .isEqualTo("registration.start.verificationFailed");
        assertThat(RegistrationWorkflowErrorKeys.P6_MEMBER_NOT_FOUND_TARGET_KEY)
                .isEqualTo(RegistrationWorkflowErrorKeys.REGISTRATION_START_VERIFICATION_FAILED);
    }

    @Test
    void workflowKeyObjectsDoNotExposeSensitiveValuesInFailures() {
        var sensitive = "Cookie auth_flow_sid=afs_secret";
        assertThatThrownBy(() -> new WorkflowErrorKey(sensitive))
                .hasMessage("Invalid workflow error key")
                .hasMessageNotContaining("auth_flow_sid")
                .hasMessageNotContaining("afs_secret")
                .hasMessageNotContaining(sensitive);
    }
}
