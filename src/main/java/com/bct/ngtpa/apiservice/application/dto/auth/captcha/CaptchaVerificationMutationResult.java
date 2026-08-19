package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.util.Objects;

public record CaptchaVerificationMutationResult(Outcome outcome, CaptchaChallenge challenge) {
  public enum Outcome {
    VERIFIED,
    INVALID,
    LOCKED,
    STATE_CHANGED,
    NOT_FOUND
  }

  public CaptchaVerificationMutationResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
  }

  public static CaptchaVerificationMutationResult of(Outcome outcome, CaptchaChallenge challenge) {
    return new CaptchaVerificationMutationResult(outcome, challenge);
  }
}
