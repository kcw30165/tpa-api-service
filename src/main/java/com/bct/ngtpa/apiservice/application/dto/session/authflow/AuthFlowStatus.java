package com.bct.ngtpa.apiservice.application.dto.session.authflow;

public enum AuthFlowStatus {
  STARTED,
  OTP_SENT,
  OTP_VERIFIED,
  TRUST_SELECTION_READY,
  USERNAME_VERIFIED,
  IDENTITY_VERIFIED,
  RESET_AUTHORIZED,
  COMPLETING,
  COMPLETED,
  CANCELLED,
  LOCKED,
  UNKNOWN_OUTCOME_BLOCKED,
  EXPIRED
}
