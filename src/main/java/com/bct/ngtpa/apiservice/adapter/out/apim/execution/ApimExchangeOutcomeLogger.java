package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Log-only observability for {@link ApimExchangeExecutor#postEnvelope} outcomes.
 *
 * <p>This component never throws and never changes the envelope or exception that flows back to
 * the calling adapter. It implements the {@code apim.contract.invalid}, {@code
 * apim.business.rejected}, and {@code apim.exchange.failed} events already named (but not
 * previously implemented anywhere) in {@code docs/integration/apim/operations-and-testing.md}.
 *
 * <p>A well-formed, non-rejected success outcome is intentionally silent here - it is already
 * covered by {@code ApimRequestLoggingExchangeFilter}'s existing outbound request log.
 */
@Component
@Slf4j
public class ApimExchangeOutcomeLogger {

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
      case BUSINESS_REJECTED -> logBusinessRejected(request, envelope, requestId);
      case SUCCESS -> {
        // Intentionally silent - see class-level Javadoc.
      }
    }
  }

  public void logExchangeFailure(
      ApimExchangeRequest<?, ?> request, Throwable error, String requestId) {
    Map<String, Object> event = baseEvent("apim.exchange.failed", request, requestId);
    event.put("exceptionType", error.getClass().getSimpleName());
    event.put("errorMessage", loggingSanitizer.sanitizeText(error.getMessage()));
    log.error("{}", toJson(event));
  }

  private void logContractInvalid(
      ApimExchangeRequest<?, ?> request, String reason, String requestId) {
    Map<String, Object> event = baseEvent("apim.contract.invalid", request, requestId);
    event.put("reason", reason);
    log.error("{}", toJson(event));
  }

  private <D> void logBusinessRejected(
      ApimExchangeRequest<?, D> request, ApimResponseEnvelope<D> envelope, String requestId) {
    Map<String, Object> event = baseEvent("apim.business.rejected", request, requestId);
    event.put("errMessage", loggingSanitizer.sanitizeText(envelope.getResponse().getErrMessage()));
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

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException ex) {
      return "{\"event\":\"log-serialization-failed\"}";
    }
  }
}
