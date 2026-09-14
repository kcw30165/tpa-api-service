package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyAction;

/** Stable shared-idempotency actions for Username Recovery. */
public enum UsernameRecoveryIdempotencyAction {
  START("start", "username-recovery-start"),
  REQUEST("request", "username-recovery-request"),
  CANCEL("cancel", "username-recovery-cancel");

  private final IdempotencyAction action;
  private final String legacyCanonicalAction;

  UsernameRecoveryIdempotencyAction(String value, String legacyCanonicalAction) {
    this.action = new IdempotencyAction(value);
    this.legacyCanonicalAction = legacyCanonicalAction;
  }

  public IdempotencyAction action() {
    return action;
  }

  String legacyCanonicalAction() {
    return legacyCanonicalAction;
  }
}
