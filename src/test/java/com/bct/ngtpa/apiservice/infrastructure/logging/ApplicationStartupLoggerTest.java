package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;

class ApplicationStartupLoggerTest {

  private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

  private final Environment environment = mock(Environment.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Logger logger;

  @BeforeEach
  void attachAppender() {
    logger = (Logger) LoggerFactory.getLogger(ApplicationStartupLogger.class);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void logsBuildCommitAndActiveProfilesOnceAtInfo() throws Exception {
    Properties properties = new Properties();
    properties.setProperty("commit", COMMIT);
    BuildProperties buildProperties = new BuildProperties(properties);
    ObjectProvider<BuildProperties> buildPropertiesProvider = mock(ObjectProvider.class);
    when(buildPropertiesProvider.getIfAvailable()).thenReturn(buildProperties);
    when(environment.getActiveProfiles()).thenReturn(new String[] {"local", "test"});

    new ApplicationStartupLogger(buildPropertiesProvider, environment, objectMapper)
        .onApplicationReady(mock(ApplicationReadyEvent.class));

    JsonNode event = singleInfoEvent();
    assertThat(event.path("event").asText()).isEqualTo("app.started");
    assertThat(event.path("commit").asText()).isEqualTo(COMMIT);
    assertThat(event.path("profiles").toString()).isEqualTo("[\"local\",\"test\"]");
  }

  @Test
  void usesUnknownWhenBuildPropertiesAreMissing() throws Exception {
    ObjectProvider<BuildProperties> buildPropertiesProvider = mock(ObjectProvider.class);
    when(buildPropertiesProvider.getIfAvailable()).thenReturn(null);
    when(environment.getActiveProfiles()).thenReturn(new String[] {"local"});

    new ApplicationStartupLogger(buildPropertiesProvider, environment, objectMapper)
        .onApplicationReady(mock(ApplicationReadyEvent.class));

    JsonNode event = singleInfoEvent();
    assertThat(event.path("event").asText()).isEqualTo("app.started");
    assertThat(event.path("commit").asText()).isEqualTo("unknown");
    assertThat(event.path("profiles").toString()).isEqualTo("[\"local\"]");
  }

  private JsonNode singleInfoEvent() throws Exception {
    assertThat(appender.list).hasSize(1);
    ILoggingEvent logged = appender.list.getFirst();
    assertThat(logged.getLevel()).isEqualTo(Level.INFO);
    return objectMapper.readTree(logged.getFormattedMessage());
  }
}
