package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.util.Objects;

public record LegacyAuthFlowSessionRevisionInitializationResult(
    LegacyAuthFlowSessionRevisionInitializationOutcome outcome, AuthFlowSessionSnapshot snapshot) {
  public LegacyAuthFlowSessionRevisionInitializationResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
    if (outcome == LegacyAuthFlowSessionRevisionInitializationOutcome.LEGACY_REVISION_INITIALIZED
        && snapshot == null) {
      throw new IllegalArgumentException("successful initialization requires snapshot");
    }
  }

  public static LegacyAuthFlowSessionRevisionInitializationResult outcome(
      LegacyAuthFlowSessionRevisionInitializationOutcome outcome) {
    return new LegacyAuthFlowSessionRevisionInitializationResult(outcome, null);
  }

  @Override
  public String toString() {
    return "LegacyAuthFlowSessionRevisionInitializationResult[outcome="
        + outcome
        + ", snapshotPresent="
        + (snapshot != null)
        + "]";
  }
}
