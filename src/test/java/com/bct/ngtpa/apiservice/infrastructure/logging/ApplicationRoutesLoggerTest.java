package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

class ApplicationRoutesLoggerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ObjectProvider<RequestMappingHandlerMapping> mappingsProvider =
      mock(ObjectProvider.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Logger logger;
  private Level originalLevel;

  @BeforeEach
  void attachAppender() {
    logger = (Logger) LoggerFactory.getLogger(ApplicationRoutesLogger.class);
    originalLevel = logger.getLevel();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
  }

  @Test
  void logsOnlyOneInfoCountWhenDebugIsDisabled() throws Exception {
    logger.setLevel(Level.INFO);
    RequestMappingHandlerMapping handlerMapping =
        handlerMapping(
            mapping("/api/v1/one", RequestMethod.GET), mapping("/api/v1/two", RequestMethod.POST));
    when(mappingsProvider.orderedStream()).thenReturn(Stream.of(handlerMapping));

    logger().onApplicationReady(mock(ApplicationReadyEvent.class));

    assertThat(appender.list).hasSize(1);
    ILoggingEvent logged = appender.list.getFirst();
    assertThat(logged.getLevel()).isEqualTo(Level.INFO);
    JsonNode event = objectMapper.readTree(logged.getFormattedMessage());
    assertThat(event.path("event").asText()).isEqualTo("app.routes");
    assertThat(event.path("registeredMappings").asInt()).isEqualTo(2);
    assertThat(logged.getFormattedMessage()).doesNotContain("/api/v1/");
  }

  @Test
  void logsOneDebugEventWithSortedMethodAndPathPatterns() throws Exception {
    logger.setLevel(Level.DEBUG);
    RequestMappingHandlerMapping handlerMapping =
        handlerMapping(
            mapping("/api/v1/zones/{zoneId}", RequestMethod.GET),
            mapping("/api/v1/alpha", RequestMethod.POST));
    when(mappingsProvider.orderedStream()).thenReturn(Stream.of(handlerMapping));

    logger().onApplicationReady(mock(ApplicationReadyEvent.class));

    assertThat(appender.list).hasSize(2);
    ILoggingEvent info =
        appender.list.stream()
            .filter(event -> event.getLevel() == Level.INFO)
            .findFirst()
            .orElseThrow();
    ILoggingEvent debug =
        appender.list.stream()
            .filter(event -> event.getLevel() == Level.DEBUG)
            .findFirst()
            .orElseThrow();
    JsonNode countEvent = objectMapper.readTree(info.getFormattedMessage());
    assertThat(countEvent.path("event").asText()).isEqualTo("app.routes");
    assertThat(countEvent.path("registeredMappings").asInt()).isEqualTo(2);

    JsonNode listEvent = objectMapper.readTree(debug.getFormattedMessage());
    assertThat(listEvent.path("event").asText()).isEqualTo("app.routes.detail");
    assertThat(listEvent.path("mappings"))
        .extracting(
            mapping -> mapping.path("method").asText() + " " + mapping.path("path").asText())
        .containsExactly("GET /api/v1/zones/{zoneId}", "POST /api/v1/alpha");
    assertThat(debug.getFormattedMessage()).doesNotContain("HandlerMethod");
  }

  @Test
  void logsUnknownWhenNoMappingBeanIsAvailable() throws Exception {
    logger.setLevel(Level.INFO);
    when(mappingsProvider.orderedStream()).thenReturn(Stream.empty());

    assertThatCode(() -> logger().onApplicationReady(mock(ApplicationReadyEvent.class)))
        .doesNotThrowAnyException();

    assertThat(appender.list).hasSize(1);
    JsonNode event = objectMapper.readTree(appender.list.getFirst().getFormattedMessage());
    assertThat(event.path("event").asText()).isEqualTo("app.routes");
    assertThat(event.path("registeredMappings").asText()).isEqualTo("unknown");
  }

  @Test
  void logsUnknownAndContinuesWhenMappingBeanCannotBeCreated() throws Exception {
    logger.setLevel(Level.INFO);
    when(mappingsProvider.orderedStream())
        .thenThrow(new BeanCreationException("mapping unavailable"));

    assertThatCode(() -> logger().onApplicationReady(mock(ApplicationReadyEvent.class)))
        .doesNotThrowAnyException();

    assertThat(appender.list).hasSize(1);
    JsonNode event = objectMapper.readTree(appender.list.getFirst().getFormattedMessage());
    assertThat(event.path("event").asText()).isEqualTo("app.routes");
    assertThat(event.path("registeredMappings").asText()).isEqualTo("unknown");
  }

  private ApplicationRoutesLogger logger() {
    return new ApplicationRoutesLogger(mappingsProvider, objectMapper);
  }

  private static RequestMappingHandlerMapping handlerMapping(RequestMappingInfo... mappings) {
    RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
    Map<RequestMappingInfo, HandlerMethod> handlerMethods = new LinkedHashMap<>();
    for (RequestMappingInfo mapping : mappings) {
      handlerMethods.put(mapping, mock(HandlerMethod.class));
    }
    when(handlerMapping.getHandlerMethods()).thenReturn(handlerMethods);
    return handlerMapping;
  }

  private static RequestMappingInfo mapping(String path, RequestMethod method) {
    return RequestMappingInfo.paths(path).methods(method).build();
  }
}
