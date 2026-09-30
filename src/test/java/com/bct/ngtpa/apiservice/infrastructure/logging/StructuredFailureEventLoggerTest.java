package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Branch-oriented tests for {@link StructuredFailureEventLogger}. */
class StructuredFailureEventLoggerTest {

  private final LoggingSanitizer sanitizer = mock(LoggingSanitizer.class);
  private final StructuredFailureEventLogger logger = new StructuredFailureEventLogger(sanitizer);

  @Test
  void nullFailureIsIgnored() {
    logger.record(null);
    verifyNoInteractions(sanitizer);
  }

  @Test
  void recordDelegatesToSanitizer() {
    when(sanitizer.toSafeString(any())).thenReturn("{}");
    FailureEvent failure =
        new FailureEvent(
            "event1",
            "req-1",
            "capability1",
            "operation1",
            FailureOrigin.LOCAL_VALIDATION,
            "internal.code",
            "public.code",
            500,
            true,
            Map.of("k", "v"));
    logger.record(failure);
    verify(sanitizer).toSafeString(any());
  }
}
