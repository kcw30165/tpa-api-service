package com.bct.ngtpa.apiservice.application.idempotency;

/** Safety classification for a failed protected-operation publisher. */
public enum IdempotencyFailureClassification {
  PROVEN_NO_SIDE_EFFECT,
  POSSIBLE_SIDE_EFFECT
}
