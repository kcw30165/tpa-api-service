package com.bct.ngtpa.apiservice.application.dto.auth.csrf;

public enum RefreshCsrfTokenStatus {
  SUCCESS,
  SESSION_MISSING,
  SESSION_INVALID,
  SESSION_NOT_USABLE,
  ORIGIN_REJECTED,
  CONFLICT,
  SYSTEM_FAILURE
}
