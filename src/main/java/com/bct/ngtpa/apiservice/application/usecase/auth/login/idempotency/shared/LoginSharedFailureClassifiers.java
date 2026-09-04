package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifier;

/** Explicit Login failure choices. Callers must select at a documented dispatch boundary. */
public final class LoginSharedFailureClassifiers {
  private LoginSharedFailureClassifiers() {}

  public static IdempotencyFailureClassifier provenPreDispatch() {
    return ignored -> IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT;
  }

  public static IdempotencyFailureClassifier possibleSideEffect() {
    return ignored -> IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT;
  }
}
