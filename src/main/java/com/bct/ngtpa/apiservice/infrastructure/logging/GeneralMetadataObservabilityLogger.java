package com.bct.ngtpa.apiservice.infrastructure.logging;

import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataObservabilityPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public final class GeneralMetadataObservabilityLogger implements GeneralMetadataObservabilityPort {
  private final ObjectMapper objectMapper;

  public void cache(String requestId, Phase phase, CacheResult cacheResult) {
    Map<String, Object> e = base("general-metadata.cache", requestId, phase);
    e.put("cacheResult", cacheResult.name());
    log.info("{}", json(e));
  }

  public void resolution(
      String requestId, Phase phase, Outcome outcome, int validatedCount, long durationMs) {
    Map<String, Object> e = base("general-metadata.resolution", requestId, phase);
    e.put("outcome", outcome.name());
    e.put("validatedCount", Math.max(0, validatedCount));
    e.put("durationMs", Math.max(0, durationMs));
    log.info("{}", json(e));
  }

  private Map<String, Object> base(String event, String requestId, Phase phase) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("event", event);
    if (requestId != null && !requestId.isBlank()) e.put("requestId", requestId);
    e.put("phase", phase.name());
    return e;
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      return "{\"event\":\"log-serialization-failed\"}";
    }
  }
}
