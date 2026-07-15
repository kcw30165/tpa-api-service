package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkflowKeyValidatorRegistrationErrorPrefixTest {

    @Test
    void acceptsErrPrefixedRegistrationPasswordErrorKey() {
        assertThatCode(() -> new WorkflowErrorKey(
                "err.registration.password.validationFailed"))
                .doesNotThrowAnyException();

        assertThat(new WorkflowErrorKey(
                "err.registration.password.validationFailed").value())
                .isEqualTo("err.registration.password.validationFailed");
    }

    @Test
    void continuesToRejectUnsafePasswordLikeKeysOutsideRegistrationVocabulary() {
        assertThatThrownBy(() -> new WorkflowErrorKey("err.other.password.secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid workflow error key");
    }

    @Test
    void registrationWorkflowErrorKeysClassInitializesWithErrPrefixedValues() {
        assertThat(RegistrationWorkflowErrorKeys.REGISTRATION_PASSWORD_VALIDATION_FAILED.value())
                .startsWith("err.");
        assertThat(RegistrationWorkflowErrorKeys.WORKFLOW_IDEMPOTENCY_MISSING.value())
                .startsWith("err.");
    }
}
