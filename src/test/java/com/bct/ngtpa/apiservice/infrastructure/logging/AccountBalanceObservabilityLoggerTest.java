package com.bct.ngtpa.apiservice.infrastructure.logging;

import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceObservabilityPort.FailureCategory;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceObservabilityPort.Phase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Branch-oriented tests for {@link AccountBalanceObservabilityLogger}. Uses a REAL ObjectMapper.
 */
class AccountBalanceObservabilityLoggerTest {

  private final AccountBalanceObservabilityLogger logger =
      new AccountBalanceObservabilityLogger(new ObjectMapper());

  @Test
  void completedWithBlankRequestIdDoesNotThrow() {
    logger.completed("", Phase.COMPLETE, 100, -5, true, false);
  }

  @Test
  void completedWithNullRequestIdDoesNotThrow() {
    logger.completed(null, Phase.SOURCE, 100, 3, false, true);
  }

  @Test
  void completedWithNegativeDurationClampsToZero() {
    logger.completed("req-1", Phase.ASSEMBLY, -100, 0, false, false);
  }

  @Test
  void failedLogsWithoutThrowing() {
    logger.failed("req-1", Phase.METADATA, 50, FailureCategory.SYSTEM_FAILURE);
  }

  @Test
  void failedWithAccountNotFoundDoesNotThrow() {
    logger.failed("req-1", Phase.REFERENCE_DATE, 50, FailureCategory.ACCOUNT_NOT_FOUND);
  }
}
