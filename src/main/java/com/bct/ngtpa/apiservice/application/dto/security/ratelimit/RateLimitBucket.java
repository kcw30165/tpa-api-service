package com.bct.ngtpa.apiservice.application.dto.security.ratelimit;

public enum RateLimitBucket {
  REGISTRATION_START_BY_IP,
  OTP_SEND_BY_DESTINATION_HASH,
  OTP_VERIFY_BY_REGISTRATION_SESSION
}
