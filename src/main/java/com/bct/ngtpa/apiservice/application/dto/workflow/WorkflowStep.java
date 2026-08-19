package com.bct.ngtpa.apiservice.application.dto.workflow;

public enum WorkflowStep {
  START,
  VERIFY_OTP,
  SELECT_TRUST,
  USERNAME_CHECK,
  PASSWORD_SETUP,
  COMPLETE,
  SUCCESS,
  CANCEL,
  WAIT,
  UNKNOWN
}
