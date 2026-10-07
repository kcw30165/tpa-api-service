package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkflowKeyValidatorLongDigitsTest {

  private static final String SAFE_MSG = "Invalid workflow error key";

  @Test
  void acceptsWhenOnlySevenConsecutiveDigitsPresent() {
    String v = "err.code.abc1234567xyz";
    assertThat(WorkflowKeyValidator.validate(v, SAFE_MSG)).isEqualTo(v);
  }

  @Test
  void rejectsWhenEightConsecutiveDigitsPresent() {
    String v = "err.code.abc12345678xyz";
    assertThatThrownBy(() -> WorkflowKeyValidator.validate(v, SAFE_MSG))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SAFE_MSG);
  }

  @Test
  void rejectsWhenNineConsecutiveDigitsPresent() {
    String v = "err.code.abc123456789xyz";
    assertThatThrownBy(() -> WorkflowKeyValidator.validate(v, SAFE_MSG))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SAFE_MSG);
  }

  @Test
  void acceptsWhenSeparatedDigitGroupsDoNotReachEight() {
    String v = "err.code.a1234.b5678.c12";
    assertThat(WorkflowKeyValidator.validate(v, SAFE_MSG)).isEqualTo(v);
  }

  @Test
  void acceptsBoundedLongSyntacticallyValidNonMatch() {
    String longSuffix = "a".repeat(10_000);
    String v = "err." + longSuffix;
    assertThat(WorkflowKeyValidator.validate(v, SAFE_MSG)).isEqualTo(v);
  }
}
