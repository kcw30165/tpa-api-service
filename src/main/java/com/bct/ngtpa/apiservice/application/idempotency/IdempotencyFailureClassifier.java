package com.bct.ngtpa.apiservice.application.idempotency;

/**
 * Action-supplied, context-neutral classification boundary. Implementations must choose possible
 * side effect whenever dispatch or effect acceptance cannot be disproved.
 */
@FunctionalInterface
public interface IdempotencyFailureClassifier {
  IdempotencyFailureClassification classify(Throwable failure);
}
