package com.bct.ngtpa.apiservice.application.dto.workflow;

public enum WorkflowStableStateRule {
    KEEP_CURRENT_STABLE_STATE,
    MOVE_TO_NEXT_STABLE_STATE,
    MOVE_TO_TERMINAL_STATE,
    RESTORE_PREVIOUS_STABLE_STATE,
    RESTART_REQUIRED
}
