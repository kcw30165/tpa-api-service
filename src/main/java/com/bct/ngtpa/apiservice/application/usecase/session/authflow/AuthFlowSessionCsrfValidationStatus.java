package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

public enum AuthFlowSessionCsrfValidationStatus {
  VALID,
  SESSION_MISSING,
  SESSION_EXPIRED,
  SESSION_NOT_USABLE,
  TOKEN_MISSING,
  TOKEN_HASH_MISSING,
  TOKEN_MISMATCH,
  ORIGIN_REFERER_INVALID,
  INVALID_INPUT
}
