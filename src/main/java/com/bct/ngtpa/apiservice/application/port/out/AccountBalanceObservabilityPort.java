package com.bct.ngtpa.apiservice.application.port.out;

public interface AccountBalanceObservabilityPort {
  enum Phase {
    COMPLETE,
    REFERENCE_DATE,
    METADATA,
    SOURCE,
    ASSEMBLY
  }

  enum FailureCategory {
    ACCOUNT_NOT_FOUND,
    ACCOUNT_MISMATCH,
    SYSTEM_FAILURE
  }

  void completed(
      String requestId,
      Phase phase,
      long durationMs,
      int validatedCount,
      boolean fundLevelViewEnabled,
      boolean subDisplayLevelViewEnabled);

  void failed(String requestId, Phase phase, long durationMs, FailureCategory failureCategory);
}
