package com.bct.ngtpa.apiservice.application.idempotency;

/**
 * Safe shared-executor failure. Messages never contain operation identities or protected values.
 */
public final class IdempotencyExecutionException extends RuntimeException {
  public IdempotencyExecutionException(String message) {
    super(message);
  }

  public IdempotencyExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
