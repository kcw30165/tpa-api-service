package com.bct.ngtpa.apiservice.infrastructure.logging;

import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataObservabilityPort.CacheResult;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataObservabilityPort.Outcome;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataObservabilityPort.Phase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Branch-oriented tests for {@link GeneralMetadataObservabilityLogger}. Uses a REAL ObjectMapper.
 */
class GeneralMetadataObservabilityLoggerTest {

  private final GeneralMetadataObservabilityLogger logger =
      new GeneralMetadataObservabilityLogger(new ObjectMapper());

  @Test
  void cacheWithBlankRequestIdDoesNotThrow() {
    logger.cache("", Phase.INITIAL_READ, CacheResult.HIT);
  }

  @Test
  void cacheWithNullRequestIdDoesNotThrow() {
    logger.cache(null, Phase.UNFILTERED, CacheResult.MISS);
  }

  @Test
  void cacheIncompleteDoesNotThrow() {
    logger.cache("req-1", Phase.CONFLICT_RECOVERY, CacheResult.INCOMPLETE);
  }

  @Test
  void resolutionSuccessDoesNotThrow() {
    logger.resolution("req-1", Phase.TARGETED, Outcome.SUCCESS, 5, 100);
  }

  @Test
  void resolutionFailureWithNegativeValuesClampsToZero() {
    logger.resolution("req-1", Phase.INITIAL_READ, Outcome.FAILURE, -1, -1);
  }
}
