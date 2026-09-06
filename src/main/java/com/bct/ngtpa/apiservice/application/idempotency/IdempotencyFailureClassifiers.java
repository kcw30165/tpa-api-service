package com.bct.ngtpa.apiservice.application.idempotency;

/** Central constant classifiers selected by integrations at explicit side-effect boundaries. */
public final class IdempotencyFailureClassifiers {
  private static final IdempotencyFailureClassifier PROVEN_PRE_DISPATCH =
      ignored -> IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT;
  private static final IdempotencyFailureClassifier POSSIBLE_SIDE_EFFECT =
      ignored -> IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT;

  private IdempotencyFailureClassifiers() {}

  public static IdempotencyFailureClassifier provenPreDispatch() {
    return PROVEN_PRE_DISPATCH;
  }

  public static IdempotencyFailureClassifier possibleSideEffect() {
    return POSSIBLE_SIDE_EFFECT;
  }
}
