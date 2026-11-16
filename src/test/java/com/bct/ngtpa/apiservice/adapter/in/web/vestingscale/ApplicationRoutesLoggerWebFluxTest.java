package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.application.port.in.GetVestingScaleUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.infrastructure.logging.ApplicationRoutesLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(
    classes = ApplicationRoutesLoggerWebFluxTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.config.name=gc-vs-008-codec-test-isolated",
      "spring.main.web-application-type=reactive",
      "spring.main.banner-mode=off"
    })
class ApplicationRoutesLoggerWebFluxTest {

  @Autowired private ApplicationRoutesLogger routesLogger;
  @Autowired private ObjectProvider<RequestMappingHandlerMapping> mappingsProvider;
  @Autowired private ObjectMapper objectMapper;

  private Logger logger;
  private Level originalLevel;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    logger = (Logger) LoggerFactory.getLogger(ApplicationRoutesLogger.class);
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender = new ListAppender<>();
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
  void logsActualVestingScaleControllerMappingFromWebFlux() throws Exception {
    List<RequestMappingHandlerMapping> handlerMappings = mappingsProvider.orderedStream().toList();
    int registeredMappings =
        handlerMappings.stream().mapToInt(mapping -> mapping.getHandlerMethods().size()).sum();
    assertThat(registeredMappings).isPositive();
    assertThat(handlerMappings)
        .flatExtracting(mapping -> mapping.getHandlerMethods().keySet())
        .anySatisfy(
            mapping -> {
              assertThat(mapping.getMethodsCondition().getMethods()).contains(RequestMethod.GET);
              assertThat(mapping.getPatternsCondition().getDirectPaths())
                  .contains("/api/v1/vesting-scale");
            });

    routesLogger.onApplicationReady(
        mock(org.springframework.boot.context.event.ApplicationReadyEvent.class));

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
    assertThat(countEvent.path("registeredMappings").asInt()).isEqualTo(registeredMappings);

    JsonNode detailEvent = objectMapper.readTree(debug.getFormattedMessage());
    assertThat(detailEvent.path("event").asText()).isEqualTo("app.routes.detail");
    assertThat(detailEvent.path("mappings"))
        .anySatisfy(
            mapping -> {
              assertThat(mapping.path("method").asText()).isEqualTo("GET");
              assertThat(mapping.path("path").asText()).isEqualTo("/api/v1/vesting-scale");
            });
    assertThat(debug.getFormattedMessage()).doesNotContain("VestingScaleController");
  }

  @TestConfiguration
  static class TestDependencies {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    GetVestingScaleUseCase getVestingScaleUseCase() {
      return mock(GetVestingScaleUseCase.class);
    }

    @Bean
    CurrentPortalAccessContextResolver currentPortalAccessContextResolver() {
      return mock(CurrentPortalAccessContextResolver.class);
    }

    @Bean
    AuthenticatedSessionCookieReader authenticatedSessionCookieReader() {
      return mock(AuthenticatedSessionCookieReader.class);
    }

    @Bean
    VestingScaleWebMapper vestingScaleWebMapper() {
      return mock(VestingScaleWebMapper.class);
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.micrometer.metrics.autoconfigure.ssl.SslMetricsAutoConfiguration"
      })
  @Import({ApplicationRoutesLogger.class, VestingScaleController.class, TestDependencies.class})
  static class TestApplication {}
}
