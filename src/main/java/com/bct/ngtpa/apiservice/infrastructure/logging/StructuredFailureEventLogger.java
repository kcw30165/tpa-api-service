package com.bct.ngtpa.apiservice.infrastructure.logging;

import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class StructuredFailureEventLogger implements FailureEventPort {
  private static final Set<FailureOrigin> WARN_ORIGINS =
      Set.of(
          FailureOrigin.LOCAL_VALIDATION,
          FailureOrigin.LOCAL_BUSINESS,
          FailureOrigin.DOWNSTREAM_BUSINESS);

  private final LoggingSanitizer loggingSanitizer;

  @Override
  public void publish(FailureEvent failure) {
    write(failure, null);
  }

  @Override
  public void publish(FailureEvent failure, Throwable cause) {
    write(failure, cause);
  }

  private void write(FailureEvent failure, Throwable cause) {
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
    addCauseMetadata(event, cause);

    String payload = loggingSanitizer.toSafeString(event);
    if (WARN_ORIGINS.contains(failure.origin())) {
      log.warn("{}", payload);
    } else if (cause == null) {
      log.error("{}", payload);
    } else {
      log.error("{}", payload, cause);
    }
  }

  private static void addCauseMetadata(LinkedHashMap<String, Object> event, Throwable cause) {
    if (cause == null) {
      return;
    }
    event.put("exceptionType", cause.getClass().getSimpleName());
    event.put("rootCauseType", rootCause(cause).getClass().getSimpleName());
    if (StringUtils.hasText(cause.getMessage())) {
      event.put("sanitizedMessage", cause.getMessage());
    }
  }

  private static Throwable rootCause(Throwable failure) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = failure;
    seen.add(current);
    while (current.getCause() != null && seen.add(current.getCause())) {
      current = current.getCause();
    }
    return current;
  }
}
