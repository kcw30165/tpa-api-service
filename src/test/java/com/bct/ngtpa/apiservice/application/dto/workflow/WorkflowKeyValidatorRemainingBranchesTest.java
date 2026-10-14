package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Closes the remaining reachable branch-coverage gaps in WorkflowKeyValidator identified by the
 * script 007 jacoco.xml investigation, after the two confirmed-dead branches were removed.
 */
class WorkflowKeyValidatorRemainingBranchesTest {

  private static final String SAFE_ERROR = "validation.failed";

  @Test
  void nullValueThrows() {
    assertThatThrownBy(() -> WorkflowKeyValidator.validate(null, SAFE_ERROR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SAFE_ERROR);
  }

  @Test
  void blankValueAfterTrimThrows() {
    assertThatThrownBy(() -> WorkflowKeyValidator.validate("   ", SAFE_ERROR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SAFE_ERROR);
  }

  @Test
  void valueNotMatchingCodeLikePatternThrows() {
    assertThatThrownBy(() -> WorkflowKeyValidator.validate("nodothere", SAFE_ERROR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SAFE_ERROR);
  }

  @Test
  void valueContainingBearerThrows() {
    assertThatThrownBy(() -> WorkflowKeyValidator.validate("svc.bearertoken", SAFE_ERROR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SAFE_ERROR);
  }

  @Test
  void valueContainingCookieThrows() {
    assertThatThrownBy(() -> WorkflowKeyValidator.validate("svc.cookievalue", SAFE_ERROR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SAFE_ERROR);
  }

  @Test
  void valueEndingWithPasswordWithoutErrLoginPrefixThrows() {
    assertThatThrownBy(() -> WorkflowKeyValidator.validate("account.password", SAFE_ERROR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SAFE_ERROR);
  }

  @Test
  void valueEndingWithPasswordButErrLoginPrefixDoesNotThrowViaThisCheck() {
    String result = WorkflowKeyValidator.validate("err.login.password", SAFE_ERROR);
    assertThat(result).isEqualTo("err.login.password");
  }

  @Test
  void valueContainingDotPasswordDotWithoutExclusionPrefixThrows() {
    assertThatThrownBy(() -> WorkflowKeyValidator.validate("account.password.reset", SAFE_ERROR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SAFE_ERROR);
  }

  @Test
  void valueContainingDotPasswordDotWithRegistrationPrefixDoesNotThrowViaThisCheck() {
    String result = WorkflowKeyValidator.validate("registration.password.reset", SAFE_ERROR);
    assertThat(result).isEqualTo("registration.password.reset");
  }

  @Test
  void valueContainingDotPasswordDotWithErrLoginPrefixDoesNotThrowViaThisCheck() {
    String result = WorkflowKeyValidator.validate("err.login.password.reset", SAFE_ERROR);
    assertThat(result).isEqualTo("err.login.password.reset");
  }
}
