package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkflowTransitionDecisionTest {

    @Test
    void successTransitionMovesToNextStableState() {
        var decision = WorkflowTransitionCheck.success(WorkflowStep.START, WorkflowNextStep.VERIFY_OTP);

        assertThat(decision.rule()).isEqualTo(WorkflowStableStateRule.MOVE_TO_NEXT_STABLE_STATE);
        assertThat(decision.currentStep()).isEqualTo(WorkflowStep.START);
        assertThat(decision.nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        assertThat(decision.failureCategory()).isNull();
        assertThat(decision.logSeverity()).isNull();
    }

    @Test
    void recoverableValidationFailureKeepsCurrentStableState() {
        var decision = WorkflowTransitionCheck.recoverableFailure(WorkflowStep.VERIFY_OTP, WorkflowNextStep.VERIFY_OTP, WorkflowFailureCategory.VALIDATION);

        assertThat(decision.rule()).isEqualTo(WorkflowStableStateRule.KEEP_CURRENT_STABLE_STATE);
        assertThat(decision.logSeverity()).isEqualTo(WorkflowLogSeverity.ERROR);
    }

    @Test
    void recoverableBusinessFailureKeepsCurrentStableState() {
        var decision = WorkflowTransitionCheck.recoverableFailure(WorkflowStep.USERNAME_CHECK, WorkflowNextStep.USERNAME_CHECK, WorkflowFailureCategory.BUSINESS);

        assertThat(decision.rule()).isEqualTo(WorkflowStableStateRule.KEEP_CURRENT_STABLE_STATE);
        assertThat(decision.logSeverity()).isEqualTo(WorkflowLogSeverity.ERROR);
    }

    @Test
    void downstreamBusinessFailureClassifiesAsError() {
        var decision = WorkflowTransitionCheck.recoverableFailure(WorkflowStep.PASSWORD_SETUP, WorkflowNextStep.PASSWORD_SETUP, WorkflowFailureCategory.DOWNSTREAM_BUSINESS);

        assertThat(decision.logSeverity()).isEqualTo(WorkflowLogSeverity.ERROR);
    }

    @Test
    void downstreamAvailabilityFailureClassifiesAsFatal() {
        var decision = WorkflowTransitionCheck.recoverableFailure(WorkflowStep.PASSWORD_SETUP, WorkflowNextStep.PASSWORD_SETUP, WorkflowFailureCategory.DOWNSTREAM_AVAILABILITY);

        assertThat(decision.logSeverity()).isEqualTo(WorkflowLogSeverity.FATAL);
    }

    @Test
    void systemFailureClassifiesAsFatal() {
        var decision = WorkflowTransitionCheck.recoverableFailure(WorkflowStep.PASSWORD_SETUP, WorkflowNextStep.PASSWORD_SETUP, WorkflowFailureCategory.SYSTEM);

        assertThat(decision.logSeverity()).isEqualTo(WorkflowLogSeverity.FATAL);
    }

    @Test
    void terminalTransitionMovesToTerminalState() {
        var decision = WorkflowTransitionCheck.terminal(WorkflowStep.COMPLETE, WorkflowNextStep.REGISTRATION_SUCCESS);

        assertThat(decision.rule()).isEqualTo(WorkflowStableStateRule.MOVE_TO_TERMINAL_STATE);
        assertThat(decision.currentStep()).isEqualTo(WorkflowStep.COMPLETE);
        assertThat(decision.nextStep()).isEqualTo(WorkflowNextStep.REGISTRATION_SUCCESS);
    }

    @Test
    void restorePreviousStateDecisionUsesPreviousStableStep() {
        var decision = WorkflowTransitionCheck.restorePrevious(WorkflowStep.PASSWORD_SETUP, WorkflowNextStep.PASSWORD_SETUP, WorkflowFailureCategory.DOWNSTREAM_BUSINESS);

        assertThat(decision.rule()).isEqualTo(WorkflowStableStateRule.RESTORE_PREVIOUS_STABLE_STATE);
        assertThat(decision.currentStep()).isEqualTo(WorkflowStep.PASSWORD_SETUP);
        assertThat(decision.nextStep()).isEqualTo(WorkflowNextStep.PASSWORD_SETUP);
        assertThat(decision.logSeverity()).isEqualTo(WorkflowLogSeverity.ERROR);
    }

    @Test
    void restartRequiredDecisionUsesRestartFlowNextStep() {
        var decision = WorkflowTransitionCheck.restartRequired(WorkflowFailureCategory.SECURITY);

        assertThat(decision.rule()).isEqualTo(WorkflowStableStateRule.RESTART_REQUIRED);
        assertThat(decision.nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
        assertThat(decision.logSeverity()).isEqualTo(WorkflowLogSeverity.ERROR);
    }

    @Test
    void transitionDecisionToStringContainsOnlySafeEnumsAndCounts() {
        var decision = WorkflowTransitionCheck.recoverableFailure(WorkflowStep.VERIFY_OTP, WorkflowNextStep.VERIFY_OTP, WorkflowFailureCategory.SECURITY);

        assertThat(decision.toString())
                .contains("KEEP_CURRENT_STABLE_STATE")
                .contains("VERIFY_OTP")
                .contains("SECURITY")
                .doesNotContain("auth_flow_sid", "csrf", "otpCode", "password", "Cookie", "Bearer");
    }
}
