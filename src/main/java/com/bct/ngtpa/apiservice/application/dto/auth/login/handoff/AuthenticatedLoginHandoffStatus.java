package com.bct.ngtpa.apiservice.application.dto.auth.login.handoff;

public enum AuthenticatedLoginHandoffStatus {
  RESERVED,
  AUTHENTICATED_SESSION_COMMITTED,
  AUTH_FLOW_DELETED,
  FAILED,
  UNKNOWN_OUTCOME
}
