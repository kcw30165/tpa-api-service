package com.bct.ngtpa.apiservice.application.dto.workflow;

public enum WorkflowNextStep {
    VERIFY_OTP,
    USERNAME_CHECK,
    SELECT_TRUST,
    PASSWORD_SETUP,
    REGISTRATION_SUCCESS,
    RETURN_TO_LOGIN,
    WAIT,
    RESTART_FLOW,
    RESEND_OTP,
    NONE
}
