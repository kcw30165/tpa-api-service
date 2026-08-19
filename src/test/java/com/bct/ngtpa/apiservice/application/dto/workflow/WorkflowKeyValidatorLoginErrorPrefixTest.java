package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkflowKeyValidatorLoginErrorPrefixTest {

  @Test
  void confirmedLoginPasswordErrorCodeIsAllowed() {
    assertThat(WorkflowKeyValidator.validate("err.login.incorrect.password", "invalid"))
        .isEqualTo("err.login.incorrect.password");
  }

  @Test
  void rawPasswordLikeKeysRemainRejected() {
    assertThatThrownBy(() -> WorkflowKeyValidator.validate("password.value", "invalid"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WorkflowKeyValidator.validate("workflow.password.secret", "invalid"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
