package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Log-only observability for {@link ApimExchangeExecutor#postEnvelope} outcomes.
 *
 * <p>This component never throws and never changes the envelope or exception that flows back to the
 * calling adapter. It implements the {@code apim.contract.invalid}, {@code apim.business.rejected},
 * and {@code apim.exchange.failed} events already named (but not previously implemented anywhere)
 * in {@code docs/integration/apim/operations-and-testing.md}.
 *
 * <p>A well-formed, non-rejected success outcome is intentionally silent here - it is already
 * covered by {@code ApimRequestLoggingExchangeFilter}'s existing outbound request log.
 */
@Component
@Slf4j
public class ApimExchangeOutcomeLogger {

  private static final int MAX_CAUSE_DEPTH = 16;

  private final LoggingSanitizer loggingSanitizer;
  private final ObjectMapper objectMapper;

  public ApimExchangeOutcomeLogger(LoggingSanitizer loggingSanitizer, ObjectMapper objectMapper) {
    this.loggingSanitizer = loggingSanitizer;
    this.objectMapper = objectMapper;
  }

  public <D> void logSuccessOutcome(
      ApimExchangeRequest<?, D> request, ApimResponseEnvelope<D> envelope, String requestId) {
    ApimEnvelopeSchemaClassification classification =
        ApimEnvelopeSchemaClassifier.classify(envelope);
    switch (classification.kind()) {
      case MALFORMED -> logContractInvalid(request, classification.malformedReason(), requestId);
      case BUSINESS_REJECTED -> logBusinessRejected(request, requestId);
      case SUCCESS -> {
        // Intentionally silent - see class-level Javadoc.
      }
    }
  }

  public void logExchangeFailure(
      ApimExchangeRequest<?, ?> request, Throwable error, String requestId) {
    Map<String, Object> event = baseEvent("apim.exchange.failed", request, requestId);
    event.put("exceptionType", error.getClass().getSimpleName());
    Throwable innermost = rootCause(error);
    if (innermost != error) {
      event.put("rootCauseType", innermost.getClass().getSimpleName());
    }
    String fieldPath = jsonPath(error);
    if (fieldPath != null && request.resubmissionPolicy() != ApimResubmissionPolicy.NEVER) {
      event.put("jsonPath", fieldPath);
    }
    log.error("{}", toJson(event));
  }

  private void logContractInvalid(
      ApimExchangeRequest<?, ?> request, String reason, String requestId) {
    Map<String, Object> event = baseEvent("apim.contract.invalid", request, requestId);
    event.put("reason", reason);
    log.error("{}", toJson(event));
  }

  private void logBusinessRejected(ApimExchangeRequest<?, ?> request, String requestId) {
    Map<String, Object> event = baseEvent("apim.business.rejected", request, requestId);
    log.warn("{}", toJson(event));
  }

  private Map<String, Object> baseEvent(
      String eventName, ApimExchangeRequest<?, ?> request, String requestId) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("event", eventName);
    if (requestId != null) {
      event.put("requestId", requestId);
    }
    event.put("path", request.path());
    // databaseScope and retryEligibility are guaranteed non-null by
    // ApimExchangeRequest's compact constructor - no defensive null-check needed.
    event.put("databaseScope", request.databaseScope().name());
    event.put("retryEligibility", request.retryEligibility().name());
    return event;
  }

  private static Throwable rootCause(Throwable error) {
    Throwable current = error;
    for (int depth = 0; depth < MAX_CAUSE_DEPTH; depth++) {
      Throwable cause = current.getCause();
      if (cause == null || cause == current) {
        break;
      }
      current = cause;
    }
    return current;
  }

  /**
   * Field names and array indexes of the first JSON mapping failure in the cause chain, for example
   * {@code response.data[0].fundDtl[0].chin-fund-fact-sheet}. Built from the path references only:
   * the exception message is never used, because Jackson messages can contain response values.
   */
  private static String jsonPath(Throwable error) {
    Throwable current = error;
    for (int depth = 0; current != null && depth <= MAX_CAUSE_DEPTH; depth++) {
      if (current instanceof JsonMappingException mapping) {
        return formatPath(mapping.getPath());
      }
      Throwable cause = current.getCause();
      current = cause == current ? null : cause;
    }
    return null;
  }

  private static String formatPath(List<JsonMappingException.Reference> path) {
    StringBuilder text = new StringBuilder();
    for (JsonMappingException.Reference reference : path) {
      if (reference.getFieldName() != null) {
        if (text.length() > 0) {
          text.append('.');
        }
        text.append(reference.getFieldName());
      } else if (reference.getIndex() >= 0) {
        text.append('[').append(reference.getIndex()).append(']');
      }
    }
    return text.length() == 0 ? null : text.toString();
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException ex) {
      return "{\"event\":\"log-serialization-failed\"}";
    }
  }
}
