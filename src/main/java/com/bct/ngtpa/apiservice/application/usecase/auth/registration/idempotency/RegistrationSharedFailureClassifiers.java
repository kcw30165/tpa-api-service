package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifier;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifiers;

/** Compatibility API delegating constant choices to the central shared classifiers. */
public final class RegistrationSharedFailureClassifiers {
  private RegistrationSharedFailureClassifiers() {}

  public static IdempotencyFailureClassifier provenPreDispatch() {
    return IdempotencyFailureClassifiers.provenPreDispatch();
  }

  public static IdempotencyFailureClassifier possibleSideEffect() {
    return IdempotencyFailureClassifiers.possibleSideEffect();
  }
}
