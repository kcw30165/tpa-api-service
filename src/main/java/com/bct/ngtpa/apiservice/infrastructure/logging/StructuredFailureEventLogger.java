package com.bct.ngtpa.apiservice.infrastructure.logging;

import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StructuredFailureEventLogger implements FailureEventPort {
  private final LoggingSanitizer loggingSanitizer;

  @Override
  public void record(FailureEvent failure) {
    if (failure == null) {
      return;
    }
    var event = new LinkedHashMap<String, Object>();
    event.put("event", failure.event());
    event.put("requestId", failure.requestId());
    event.put("capability", failure.capability());
    event.put("operation", failure.operation());
    event.put("failureOrigin", failure.origin());
    event.put("internalCode", failure.internalCode());
    event.put("publicCode", failure.publicCode());
    event.put("httpStatus", failure.httpStatus());
    event.put("retryable", failure.retryable());
    event.put("dimensions", failure.safeDimensions());
    log.error("{}", loggingSanitizer.toSafeString(event));
  }
}
