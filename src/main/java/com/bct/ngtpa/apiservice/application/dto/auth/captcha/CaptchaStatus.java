package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

public enum CaptchaStatus {
  CREATED,
  VERIFIED,
  CONSUMED,
  REFRESHED,
  EXPIRED,
  LOCKED
}
