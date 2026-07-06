package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WorkflowStableStateRuleTest {

    @Test
    void stableStateRuleDefinesExpectedValues() {
        assertThat(WorkflowStableStateRule.values()).containsExactly(
                WorkflowStableStateRule.KEEP_CURRENT_STABLE_STATE,
                WorkflowStableStateRule.MOVE_TO_NEXT_STABLE_STATE,
                WorkflowStableStateRule.MOVE_TO_TERMINAL_STATE,
                WorkflowStableStateRule.RESTORE_PREVIOUS_STABLE_STATE,
                WorkflowStableStateRule.RESTART_REQUIRED);
    }

    @Test
    void workflowTransitionDoesNotUseFailedUnknownErrorStatuses() {
        assertThat(Arrays.stream(WorkflowStep.values()).map(Enum::name))
                .doesNotContain("FAILED", "UNKNOWN_FAILURE", "ERROR");
        assertThat(Arrays.stream(WorkflowNextStep.values()).map(Enum::name))
                .doesNotContain("FAILED", "UNKNOWN_FAILURE", "ERROR");
        assertThat(Arrays.stream(WorkflowStableStateRule.values()).map(Enum::name))
                .doesNotContain("FAILED", "UNKNOWN", "UNKNOWN_FAILURE", "ERROR");
    }
}
