package com.bct.ngtpa.apiservice.infrastructure.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public final class ApplicationStartupLogger {

  private static final String UNKNOWN_COMMIT = "unknown";

  private final ObjectProvider<BuildProperties> buildPropertiesProvider;
  private final Environment environment;
  private final ObjectMapper objectMapper;

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady(ApplicationReadyEvent event) {
    BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
    String commit = buildProperties == null ? null : buildProperties.get("commit");
    if (!StringUtils.hasText(commit)) {
      commit = UNKNOWN_COMMIT;
    }

    Map<String, Object> startupEvent = new LinkedHashMap<>();
    startupEvent.put("event", "app.started");
    startupEvent.put("commit", commit);
    startupEvent.put("profiles", List.of(environment.getActiveProfiles()));
    log.info("{}", toJson(startupEvent));
  }

  private String toJson(Map<String, Object> startupEvent) {
    try {
      return objectMapper.writeValueAsString(startupEvent);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize app.started event", exception);
    }
  }
}
