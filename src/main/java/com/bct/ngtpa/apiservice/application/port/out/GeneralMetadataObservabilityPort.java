package com.bct.ngtpa.apiservice.application.port.out;

public interface GeneralMetadataObservabilityPort {
  enum Phase {
    INITIAL_READ,
    UNFILTERED,
    TARGETED,
    CONFLICT_RECOVERY
  }

  enum CacheResult {
    HIT,
    MISS,
    INCOMPLETE
  }

  enum Outcome {
    SUCCESS,
    FAILURE
  }

  void cache(String requestId, Phase phase, CacheResult cacheResult);

  void resolution(
      String requestId, Phase phase, Outcome outcome, int validatedCount, long durationMs);
}
