package com.bct.ngtpa.apiservice.application.dto.auth.otp;

public enum OtpVerificationOutcome {
  VALID,
  INVALID,
  EXPIRED,
  LOCKED,
  INVALIDATED,
  ALREADY_VERIFIED,
  NOT_FOUND
}
