package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import java.util.Objects;

/**
 * Application result of the Username Recovery cancel flow.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 8 UR-CANCEL-001
 * (valid or absent session is an idempotent success) and UR-CANCEL-003 (mapped infrastructure
 * exceptions become a system failure).
 *
 * <p>Invariants: {@code outcome} is required. Cookie clearing is a web concern and is not modeled
 * here.
 */
public record UsernameRecoveryCancelResult(Outcome outcome) {

  public enum Outcome {
    SUCCESS,
    SYSTEM_FAILED
  }

  public UsernameRecoveryCancelResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
  }

  @Override
  public String toString() {
    return "UsernameRecoveryCancelResult[outcome=" + outcome + "]";
  }
}
