package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForgotPasswordCompleteProtectedOperationFoundationTest {
  @Test
  void terminalClassificationAcceptsCapturableSuccessAndBusinessRejection() {
    var success =
        new CompleteForgotPasswordResult(
            CompleteForgotPasswordStatus.SUCCESS,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            true,
            List.of());
    var rejection =
        new CompleteForgotPasswordResult(
            CompleteForgotPasswordStatus.BUSINESS_REJECTED,
            WorkflowNextStep.PASSWORD_SETUP,
            false,
            false,
            List.of());

    assertThat(ForgotPasswordCompleteProtectedExecutionResult.terminal(success).outcome())
        .isEqualTo(ForgotPasswordCompleteProtectedExecutionResult.Outcome.TERMINAL);
    assertThat(ForgotPasswordCompleteProtectedExecutionResult.terminal(rejection).outcome())
        .isEqualTo(ForgotPasswordCompleteProtectedExecutionResult.Outcome.TERMINAL);
  }

  @Test
  void lifecycleUncertaintyCannotBeMisclassifiedAsTerminal() {
    var unknown =
        new CompleteForgotPasswordResult(
            CompleteForgotPasswordStatus.UNKNOWN_OUTCOME,
            WorkflowNextStep.NONE,
            false,
            false,
            List.of());
    var failed =
        new CompleteForgotPasswordResult(
            CompleteForgotPasswordStatus.SYSTEM_FAILED,
            WorkflowNextStep.PASSWORD_SETUP,
            false,
            false,
            List.of());

    assertThatThrownBy(() -> ForgotPasswordCompleteProtectedExecutionResult.terminal(unknown))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ForgotPasswordCompleteProtectedExecutionResult.terminal(failed))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(ForgotPasswordCompleteProtectedExecutionResult.unknownOutcome(unknown).outcome())
        .isEqualTo(ForgotPasswordCompleteProtectedExecutionResult.Outcome.UNKNOWN_OUTCOME);
    assertThat(ForgotPasswordCompleteProtectedExecutionResult.technicalFailure(failed).outcome())
        .isEqualTo(ForgotPasswordCompleteProtectedExecutionResult.Outcome.TECHNICAL_FAILURE);
  }
}
