package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkflowKeyValidatorRegistrationErrorPrefixTest {

  @Test
  void acceptsErrPrefixedRegistrationPasswordErrorKey() {
    assertThatCode(() -> WorkflowErrorKey.of("err.registration.password.validationFailed"))
        .doesNotThrowAnyException();

    assertThat(WorkflowErrorKey.of("err.registration.password.validationFailed").value())
        .isEqualTo("err.registration.password.validationFailed");
  }

  @Test
  void continuesToRejectUnsafePasswordLikeKeysOutsideRegistrationVocabulary() {
    assertThatThrownBy(() -> WorkflowErrorKey.of("err.other.password.secret"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid workflow error key");
  }

  @Test
  void registrationWorkflowErrorKeysClassInitializesWithErrPrefixedValues() {
    assertThat(RegistrationErrorKey.REGISTRATION_PASSWORD_VALIDATIONFAILED.value())
        .startsWith("err.");
    assertThat(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value()).startsWith("err.");
  }
}
