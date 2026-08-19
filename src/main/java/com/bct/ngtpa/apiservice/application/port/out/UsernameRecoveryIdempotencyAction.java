package com.bct.ngtpa.apiservice.application.port.out;

/**
 * Idempotency action identity for protected Username Recovery mutations.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} sections 4-6 (start,
 * request, and cancel are protected mutations carrying an Idempotency-Key) and {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 4 (idempotency
 * operation ownership). Mirrors {@code RegistrationIdempotencyAction} naming.
 */
public enum UsernameRecoveryIdempotencyAction {
  START("username-recovery-start"),
  REQUEST("username-recovery-request"),
  CANCEL("username-recovery-cancel");

  private final String keyNamespace;

  UsernameRecoveryIdempotencyAction(String keyNamespace) {
    this.keyNamespace = keyNamespace;
  }

  public String keyNamespace() {
    return keyNamespace;
  }
}
