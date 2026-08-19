package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import java.util.Objects;

public record CaptchaConsumptionMutationResult(Outcome outcome, CaptchaChallenge challenge) {
  public enum Outcome {
    CONSUMED,
    STATE_CHANGED,
    NOT_FOUND
  }

  public CaptchaConsumptionMutationResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
    if (outcome == Outcome.CONSUMED && challenge == null) {
      throw new IllegalArgumentException("consumed challenge must not be null");
    }
  }

  public static CaptchaConsumptionMutationResult of(Outcome outcome, CaptchaChallenge challenge) {
    return new CaptchaConsumptionMutationResult(outcome, challenge);
  }
}
