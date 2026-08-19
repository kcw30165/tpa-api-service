package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

public enum AuthenticatedSessionCsrfRotationStatus {
  ROTATED,
  CONFLICT,
  NOT_FOUND,
  EXPIRED,
  SYSTEM_FAILURE
}
