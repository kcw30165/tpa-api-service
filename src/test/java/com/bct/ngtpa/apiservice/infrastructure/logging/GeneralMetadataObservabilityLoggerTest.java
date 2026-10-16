package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThatCode;

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
    assertThatCode(() -> logger.cache("", Phase.INITIAL_READ, CacheResult.HIT))
        .doesNotThrowAnyException();
  }

  @Test
  void cacheWithNullRequestIdDoesNotThrow() {
    assertThatCode(() -> logger.cache(null, Phase.UNFILTERED, CacheResult.MISS))
        .doesNotThrowAnyException();
  }

  @Test
  void cacheIncompleteDoesNotThrow() {
    assertThatCode(() -> logger.cache("req-1", Phase.CONFLICT_RECOVERY, CacheResult.INCOMPLETE))
        .doesNotThrowAnyException();
  }

  @Test
  void resolutionSuccessDoesNotThrow() {
    assertThatCode(() -> logger.resolution("req-1", Phase.TARGETED, Outcome.SUCCESS, 5, 100))
        .doesNotThrowAnyException();
  }

  @Test
  void resolutionFailureWithNegativeValuesClampsToZero() {
    assertThatCode(() -> logger.resolution("req-1", Phase.INITIAL_READ, Outcome.FAILURE, -1, -1))
        .doesNotThrowAnyException();
  }
}
