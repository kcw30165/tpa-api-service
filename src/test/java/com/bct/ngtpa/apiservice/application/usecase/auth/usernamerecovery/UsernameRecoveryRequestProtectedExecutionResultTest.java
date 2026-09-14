package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import org.junit.jupiter.api.Test;

class UsernameRecoveryRequestProtectedExecutionResultTest {

  @Test
  void completedCarriesOnlyTheDeterministicPublicResult() {
    UsernameRecoveryRequestResult result =
        new UsernameRecoveryRequestResult(
            UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED, null, null, null, null);

    var execution = UsernameRecoveryRequestProtectedExecutionResult.completed(result);

    assertThat(execution.completed()).isTrue();
    assertThat(execution.result()).isSameAs(result);
    assertThat(execution.failure()).isNull();
    assertThat(execution.failureBoundary())
        .isEqualTo(UsernameRecoveryRequestProtectedExecutionResult.FailureBoundary.NONE);
  }

  @Test
  void provenPreDispatchCarriesNoTerminalResult() {
    RuntimeException failure = new RuntimeException("sensitive detail");

    var execution = UsernameRecoveryRequestProtectedExecutionResult.provenPreDispatch(failure);

    assertThat(execution.completed()).isFalse();
    assertThat(execution.result()).isNull();
    assertThat(execution.failure()).isSameAs(failure);
    assertThat(execution.failureBoundary())
        .isEqualTo(
            UsernameRecoveryRequestProtectedExecutionResult.FailureBoundary.PROVEN_PRE_DISPATCH);
  }

  @Test
  void possibleSideEffectCarriesNoTerminalResult() {
    RuntimeException failure = new RuntimeException("sensitive detail");

    var execution = UsernameRecoveryRequestProtectedExecutionResult.possibleSideEffect(failure);

    assertThat(execution.completed()).isFalse();
    assertThat(execution.result()).isNull();
    assertThat(execution.failure()).isSameAs(failure);
    assertThat(execution.failureBoundary())
        .isEqualTo(
            UsernameRecoveryRequestProtectedExecutionResult.FailureBoundary.POSSIBLE_SIDE_EFFECT);
  }

  @Test
  void invalidCombinationsFailClosed() {
    UsernameRecoveryRequestResult result =
        new UsernameRecoveryRequestResult(
            UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED, null, null, null, null);
    RuntimeException failure = new RuntimeException("failure");

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new UsernameRecoveryRequestProtectedExecutionResult(
                    null,
                    UsernameRecoveryRequestProtectedExecutionResult.FailureBoundary.NONE,
                    null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new UsernameRecoveryRequestProtectedExecutionResult(
                    result,
                    UsernameRecoveryRequestProtectedExecutionResult.FailureBoundary.NONE,
                    failure));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new UsernameRecoveryRequestProtectedExecutionResult(
                    result,
                    UsernameRecoveryRequestProtectedExecutionResult.FailureBoundary
                        .POSSIBLE_SIDE_EFFECT,
                    failure));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new UsernameRecoveryRequestProtectedExecutionResult(
                    null,
                    UsernameRecoveryRequestProtectedExecutionResult.FailureBoundary
                        .PROVEN_PRE_DISPATCH,
                    null));
  }

  @Test
  void diagnosticsDoNotExposeFailureMessageOrProtectedValues() {
    RuntimeException failure =
        new RuntimeException("afs_secret username@example.test csrf_secret delivery-payload");

    String rendered =
        UsernameRecoveryRequestProtectedExecutionResult.possibleSideEffect(failure).toString();

    assertThat(rendered)
        .contains("failureType=RuntimeException")
        .doesNotContain(
            failure.getMessage(),
            "afs_secret",
            "username@example.test",
            "csrf_secret",
            "delivery-payload");
  }
}
