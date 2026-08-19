package com.bct.ngtpa.apiservice.application.dto.auth.login.handoff;

public enum AuthenticatedLoginHandoffCreateOutcome {
  CREATED,
  EXISTING,
  CONFLICT,
  CORRUPT_STATE,
  SYSTEM_FAILURE
}
