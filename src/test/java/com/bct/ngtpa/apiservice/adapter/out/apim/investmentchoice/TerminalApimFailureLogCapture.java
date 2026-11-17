package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimAppCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfileResolver;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimAesKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimPayloadFieldTransformer;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimRsaPayloadCrypto;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeOutcomeLogger;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.DefaultApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

final class TerminalApimFailureLogCapture implements AutoCloseable {

  private static final String PRIVATE_VALUE = "private-apim-value";

  private final ApimExchangeExecutor executor;
  private final FailureEventPort failureEvents;
  private final List<Logger> loggers;
  private final List<ListAppender<ILoggingEvent>> appenders;

  private TerminalApimFailureLogCapture(
      ApimExchangeExecutor executor,
      FailureEventPort failureEvents,
      List<Logger> loggers,
      List<ListAppender<ILoggingEvent>> appenders) {
    this.executor = executor;
    this.failureEvents = failureEvents;
    this.loggers = loggers;
    this.appenders = appenders;
  }

  static TerminalApimFailureLogCapture start() {
    return start(HttpStatus.BAD_GATEWAY, PRIVATE_VALUE);
  }

  static TerminalApimFailureLogCapture startBusinessRejection(String downstreamCode) {
    String body = "{\"response\":{\"err-message\":\"" + downstreamCode + "\"}}";
    return start(HttpStatus.OK, body);
  }

  private static TerminalApimFailureLogCapture start(HttpStatus status, String body) {
    ObjectMapper objectMapper = new ObjectMapper();
    ApimProperties properties = new ApimProperties();
    properties.getEncryption().setEnabled(false);
    ExchangeFunction exchangeFunction =
        request ->
            Mono.just(
                ClientResponse.create(status)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
    WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
    ApimCredentialProfile profile =
        new ApimCredentialProfile(
            "profile",
            "client",
            "client-secret",
            "api-key",
            null,
            "http://apim.test",
            List.of(),
            null);
    ApimCredentialProfileResolver resolver = (scope, context) -> Mono.just(profile);
    ApimTokenService tokens =
        new ApimTokenService(builder, properties) {
          @Override
          public Mono<String> getAccessToken(ApimCredentialProfile credentialProfile) {
            return Mono.just("access-token");
          }
        };
    ApimWebClientFacade facade =
        new ApimWebClientFacade(
            WebClient.create(),
            builder,
            objectMapper,
            resolver,
            tokens,
            new ApimCertificateService(builder, properties, null),
            properties);
    ApimPayloadCryptoService crypto =
        new ApimPayloadCryptoService(
            properties,
            objectMapper,
            mock(ApimPayloadFieldTransformer.class),
            mock(ApimRsaPayloadCrypto.class),
            mock(ApimAesKeyFactory.class),
            mock(ApimAppCertificateService.class));
    var executor =
        new DefaultApimExchangeExecutor(
            facade, crypto, properties, new ApimExchangeOutcomeLogger(objectMapper));
    LoggingSanitizer sanitizer =
        new LoggingSanitizer(objectMapper, new MaskingService(new MaskingProperties()));
    FailureEventPort failureEvents = new StructuredFailureEventLogger(sanitizer);
    List<Logger> loggers =
        List.of(
            (Logger) LoggerFactory.getLogger(ApimWebClientFacade.class),
            (Logger) LoggerFactory.getLogger(ApimExchangeOutcomeLogger.class),
            (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class));
    List<ListAppender<ILoggingEvent>> appenders = new ArrayList<>();
    for (Logger logger : loggers) {
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
      appenders.add(appender);
    }
    return new TerminalApimFailureLogCapture(executor, failureEvents, loggers, appenders);
  }

  ApimExchangeExecutor executor() {
    return executor;
  }

  FailureEventPort failureEvents() {
    return failureEvents;
  }

  void assertSingleTerminalError(String featureEvent, String... privateValues) {
    List<ILoggingEvent> events =
        appenders.stream().flatMap(appender -> appender.list.stream()).toList();
    List<ILoggingEvent> facadeEvents =
        events.stream()
            .filter(event -> event.getLoggerName().equals(ApimWebClientFacade.class.getName()))
            .toList();
    List<ILoggingEvent> outcomeEvents =
        events.stream()
            .filter(
                event -> event.getLoggerName().equals(ApimExchangeOutcomeLogger.class.getName()))
            .toList();
    assertThat(facadeEvents)
        .isNotEmpty()
        .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    assertThat(facadeEvents)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(
            message -> assertThat(message).contains("APIM request failed statusClass=HTTP_ERROR"));
    assertThat(outcomeEvents)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage()).contains("apim.exchange.failed");
            });

    List<ILoggingEvent> errors =
        events.stream().filter(event -> event.getLevel().equals(Level.ERROR)).toList();
    assertThat(errors)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLoggerName())
                  .isEqualTo(StructuredFailureEventLogger.class.getName());
              assertThat(event.getFormattedMessage()).contains(featureEvent);
              assertThat(event.getThrowableProxy()).isNull();
            });
    List<String> messages = events.stream().map(ILoggingEvent::getFormattedMessage).toList();
    assertThat(messages)
        .noneSatisfy(
            message ->
                assertThat(message).contains(PRIVATE_VALUE, "client-secret", "access-token"));
    for (String privateValue : privateValues) {
      assertThat(messages).noneSatisfy(message -> assertThat(message).contains(privateValue));
    }
  }

  void assertBusinessRejectionWithoutError(
      String featureEvent, String downstreamCode, String... privateValues) {
    List<ILoggingEvent> events =
        appenders.stream().flatMap(appender -> appender.list.stream()).toList();
    assertThat(events).isNotEmpty().noneMatch(event -> event.getLevel().equals(Level.ERROR));

    List<ILoggingEvent> apimEvents =
        events.stream()
            .filter(
                event -> event.getLoggerName().equals(ApimExchangeOutcomeLogger.class.getName()))
            .toList();
    assertThat(apimEvents)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage()).contains("apim.business.rejected");
            });

    List<ILoggingEvent> featureEvents =
        events.stream()
            .filter(
                event -> event.getLoggerName().equals(StructuredFailureEventLogger.class.getName()))
            .toList();
    assertThat(featureEvents)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage()).contains(featureEvent, downstreamCode);
              assertThat(event.getThrowableProxy()).isNull();
            });
    List<String> messages = events.stream().map(ILoggingEvent::getFormattedMessage).toList();
    for (String privateValue : privateValues) {
      assertThat(messages).noneSatisfy(message -> assertThat(message).contains(privateValue));
    }
  }

  @Override
  public void close() {
    for (int index = 0; index < loggers.size(); index++) {
      loggers.get(index).detachAppender(appenders.get(index));
      appenders.get(index).stop();
    }
  }
}
