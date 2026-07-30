package com.bct.ngtpa.apiservice.application.dto.workflow;

public enum WorkflowNextStep {
    SELECT_OTP_DESTINATION,
    VERIFY_OTP,
    ENTER_USERNAME,
    ENTER_ID,
    USERNAME_CHECK,
    ENTER_DETAILS,
    SELECT_TRUST,
    SELECT_ACCOUNT,
    PASSWORD_SETUP,
    REGISTRATION_SUCCESS,
    PASSWORD_RESET_SUCCESS,
    RETURN_TO_LOGIN,
    WAIT,
    RESTART_FLOW,
    RESEND_OTP,
    NONE
}
