package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifier;

/** Conservative classifier factories used by later action migrations at explicit boundaries. */
public final class RegistrationSharedFailureClassifiers {
  private RegistrationSharedFailureClassifiers() {}

  public static IdempotencyFailureClassifier provenPreDispatch() {
    return ignored -> IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT;
  }

  public static IdempotencyFailureClassifier possibleSideEffect() {
    return ignored -> IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT;
  }
}
