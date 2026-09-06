package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifier;

/** Explicit classifiers selected only at action-owned dispatch boundaries. */
public final class ForgotPasswordSharedFailureClassifiers {
  private ForgotPasswordSharedFailureClassifiers() {}

  public static IdempotencyFailureClassifier provenPreDispatch() {
    return ignored -> IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT;
  }

  public static IdempotencyFailureClassifier possibleSideEffect() {
    return ignored -> IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT;
  }
}
