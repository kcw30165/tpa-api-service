package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkflowSeverityMapperTest {

    @Test
    void severityMapperDelegatesToFailureClassification() {
        assertThat(WorkflowSeverityMapper.severityFor(WorkflowFailureCategory.VALIDATION))
                .isEqualTo(WorkflowLogSeverity.ERROR);
        assertThat(WorkflowSeverityMapper.severityFor(WorkflowFailureCategory.BUSINESS))
                .isEqualTo(WorkflowLogSeverity.ERROR);
        assertThat(WorkflowSeverityMapper.severityFor(WorkflowFailureCategory.SECURITY))
                .isEqualTo(WorkflowLogSeverity.ERROR);
        assertThat(WorkflowSeverityMapper.severityFor(WorkflowFailureCategory.DOWNSTREAM_BUSINESS))
                .isEqualTo(WorkflowLogSeverity.ERROR);
        assertThat(WorkflowSeverityMapper.severityFor(WorkflowFailureCategory.DOWNSTREAM_AVAILABILITY))
                .isEqualTo(WorkflowLogSeverity.FATAL);
        assertThat(WorkflowSeverityMapper.severityFor(WorkflowFailureCategory.SYSTEM))
                .isEqualTo(WorkflowLogSeverity.FATAL);
    }

    @Test
    void terminalStepPolicyRecognizesRegistrationSuccess() {
        assertThat(WorkflowTerminalStepPolicy.isTerminalNextStep(WorkflowNextStep.REGISTRATION_SUCCESS)).isTrue();
        assertThat(WorkflowTerminalStepPolicy.isTerminalNextStep(WorkflowNextStep.PASSWORD_RESET_SUCCESS)).isTrue();
        assertThat(WorkflowTerminalStepPolicy.isTerminalNextStep(WorkflowNextStep.RETURN_TO_LOGIN)).isTrue();
        assertThat(WorkflowTerminalStepPolicy.isTerminalNextStep(WorkflowNextStep.ENTER_USERNAME)).isFalse();
        assertThat(WorkflowTerminalStepPolicy.isTerminalNextStep(WorkflowNextStep.ENTER_ID)).isFalse();
        assertThat(WorkflowTerminalStepPolicy.isTerminalNextStep(WorkflowNextStep.ENTER_DETAILS)).isFalse();
        assertThat(WorkflowTerminalStepPolicy.isRestartStep(WorkflowNextStep.RESTART_FLOW)).isTrue();
        assertThat(WorkflowTerminalStepPolicy.isWaitStep(WorkflowNextStep.WAIT)).isTrue();
        assertThat(WorkflowTerminalStepPolicy.isTerminalNextStep(WorkflowNextStep.NONE)).isFalse();
        assertThat(WorkflowTerminalStepPolicy.isRestartStep(WorkflowNextStep.NONE)).isFalse();
        assertThat(WorkflowTerminalStepPolicy.isWaitStep(WorkflowNextStep.NONE)).isFalse();
    }
}
