package com.bct.ngtpa.apiservice.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateCacheUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RefreshReferenceDateServiceTest {

  @Test
  void commandRejectsNullAccountEnv() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RefreshReferenceDateCommand(
                    null, com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR));

    assertEquals("accountEnv must not be blank", ex.getMessage());
  }

  @Test
  void commandRejectsBlankAccountEnv() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RefreshReferenceDateCommand(
                    "  ", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR));

    assertEquals("accountEnv must not be blank", ex.getMessage());
  }

  @Test
  void apimSuccess_updatesRedisWithReferenceDateKeyAndValue() {
    AtomicReference<String> capturedAccountEnv = new AtomicReference<>();
    ApimReferenceDateRefreshPort apimPort =
        command -> {
          capturedAccountEnv.set(command.accountEnv());
          return Mono.just(LocalDate.of(2025, 12, 31));
        };
    AtomicReference<ReferenceDateCacheUpdateCommand> capturedCacheCommand = new AtomicReference<>();
    ReferenceDateCacheUpdatePort cachePort =
        command -> {
          capturedCacheCommand.set(command);
          return Mono.empty();
        };

    RefreshReferenceDateUseCase service =
        new RefreshReferenceDateService(apimPort, cachePort, "ngtpa");

    StepVerifier.create(
            service.execute(
                new RefreshReferenceDateCommand(
                    "JP", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR)))
        .assertNext(
            result -> {
              assertEquals("JP", capturedAccountEnv.get());
              assertEquals(
                  new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "31/12/2025"),
                  capturedCacheCommand.get());
              assertEquals(new RefreshReferenceDateResult("JP", "31/12/2025", true), result);
            })
        .verifyComplete();
  }

  @Test
  void apimFailure_skipsRedisAndPropagatesError() {
    ApimReferenceDateRefreshPort apimPort =
        command -> Mono.error(new IllegalStateException("apim fetch failed"));
    AtomicBoolean cacheCalled = new AtomicBoolean(false);
    ReferenceDateCacheUpdatePort cachePort =
        command -> {
          cacheCalled.set(true);
          return Mono.empty();
        };

    RefreshReferenceDateUseCase service =
        new RefreshReferenceDateService(apimPort, cachePort, "ngtpa");

    StepVerifier.create(
            service.execute(
                new RefreshReferenceDateCommand(
                    "JP", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR)))
        .expectErrorSatisfies(
            error -> {
              assertEquals(IllegalStateException.class, error.getClass());
              assertEquals("apim fetch failed", error.getMessage());
              assertEquals(false, cacheCalled.get());
            })
        .verify();
  }

  @Test
  void redisFailure_returnsPartialSuccessAndLogsSanitizedWarning() {
    ApimReferenceDateRefreshPort apimPort = command -> Mono.just(LocalDate.of(2025, 12, 31));
    ReferenceDateCacheUpdatePort cachePort =
        command ->
            Mono.error(
                new CacheException(
                    "Cache set operation failed",
                    new RuntimeException("lettuce write failed redis://secret-host:6379")));

    var logger = (Logger) LoggerFactory.getLogger(RefreshReferenceDateService.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      RefreshReferenceDateUseCase service =
          new RefreshReferenceDateService(apimPort, cachePort, "ngtpa");

      StepVerifier.create(
              service.execute(
                  new RefreshReferenceDateCommand(
                      "JP", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR)))
          .assertNext(
              result ->
                  assertEquals(new RefreshReferenceDateResult("JP", "31/12/2025", false), result))
          .verifyComplete();

      boolean hasSanitizedWarn =
          appender.list.stream()
              .anyMatch(
                  event ->
                      event.getLevel() == Level.WARN
                          && event.getFormattedMessage().contains("reference-date")
                          && !event.getFormattedMessage().contains("secret-host")
                          && !event.getFormattedMessage().contains("6379")
                          && !event.getFormattedMessage().contains("redis://"));
      assertEquals(true, hasSanitizedWarn);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void refreshServiceDoesNotDependOnConfigServicePorts() {
    Field[] fields = RefreshReferenceDateService.class.getDeclaredFields();

    assertEquals(
        true,
        Arrays.stream(fields)
            .anyMatch(field -> field.getType().equals(ApimReferenceDateRefreshPort.class)));
    assertEquals(
        true,
        Arrays.stream(fields)
            .anyMatch(field -> field.getType().equals(ReferenceDateCacheUpdatePort.class)));
    assertEquals(
        false,
        Arrays.stream(fields).anyMatch(field -> field.getType().equals(ConfigServicePort.class)));
  }

  @Test
  void applicationContractsUseApplicationFacingTypes() throws NoSuchMethodException {
    assertEquals(
        String.class, RefreshReferenceDateCommand.class.getRecordComponents()[0].getType());
    assertEquals(
        String.class, ReferenceDateCacheUpdateCommand.class.getRecordComponents()[0].getType());
    assertEquals(
        String.class, ReferenceDateCacheUpdateCommand.class.getRecordComponents()[1].getType());

    assertEquals(String.class, RefreshReferenceDateResult.class.getRecordComponents()[0].getType());
    assertEquals(String.class, RefreshReferenceDateResult.class.getRecordComponents()[1].getType());
    assertEquals(
        boolean.class, RefreshReferenceDateResult.class.getRecordComponents()[2].getType());
    assertEquals(3, RefreshReferenceDateResult.class.getRecordComponents().length);

    ParameterizedType apimReturnType =
        (ParameterizedType)
            ApimReferenceDateRefreshPort.class
                .getMethod(
                    "fetchReferenceDate",
                    com.bct.ngtpa.apiservice.application.dto.FetchReferenceDateCommand.class)
                .getGenericReturnType();
    assertEquals(Mono.class, apimReturnType.getRawType());
    assertEquals(LocalDate.class, apimReturnType.getActualTypeArguments()[0]);

    ParameterizedType useCaseReturnType =
        (ParameterizedType)
            RefreshReferenceDateUseCase.class
                .getMethod("execute", RefreshReferenceDateCommand.class)
                .getGenericReturnType();
    assertEquals(Mono.class, useCaseReturnType.getRawType());
    assertEquals(RefreshReferenceDateResult.class, useCaseReturnType.getActualTypeArguments()[0]);

    ParameterizedType cacheReturnType =
        (ParameterizedType)
            ReferenceDateCacheUpdatePort.class
                .getMethod("updateReferenceDate", ReferenceDateCacheUpdateCommand.class)
                .getGenericReturnType();
    assertEquals(Mono.class, cacheReturnType.getRawType());
    assertEquals(Void.class, cacheReturnType.getActualTypeArguments()[0]);
  }
}
