package com.bct.ngtpa.apiservice.application.usecase;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateCacheUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateConfigUpsertCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateConfigPort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefreshReferenceDateServiceTest {

    @Test
    void commandRejectsNullAccountEnv() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RefreshReferenceDateCommand(null));

        assertEquals("accountEnv must not be blank", ex.getMessage());
    }

    @Test
    void commandRejectsBlankAccountEnv() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RefreshReferenceDateCommand("  "));

        assertEquals("accountEnv must not be blank", ex.getMessage());
    }

    @Test
    void afterConfigUpsertSuccess_updatesRedisWithReferenceDateKeyAndValue() {
        AtomicReference<String> capturedAccountEnv = new AtomicReference<>();
        ApimReferenceDateRefreshPort apimPort = accountEnv -> {
            capturedAccountEnv.set(accountEnv);
            return Mono.just(LocalDate.of(2025, 12, 31));
        };
        AtomicReference<ReferenceDateConfigUpsertCommand> capturedConfigCommand = new AtomicReference<>();
        ReferenceDateConfigPort configPort = command -> {
            capturedConfigCommand.set(command);
            return Mono.empty();
        };
        AtomicReference<ReferenceDateCacheUpdateCommand> capturedCacheCommand = new AtomicReference<>();
        ReferenceDateCacheUpdatePort cachePort = command -> {
            capturedCacheCommand.set(command);
            return Mono.empty();
        };

        RefreshReferenceDateUseCase service = new RefreshReferenceDateService(apimPort, configPort, cachePort, "ngtpa");

        StepVerifier.create(service.execute(new RefreshReferenceDateCommand("JP")))
                .assertNext(result -> {
                    assertEquals("JP", capturedAccountEnv.get());
                    assertEquals(new ReferenceDateConfigUpsertCommand("reference-date.JP", "31/12/2025"),
                            capturedConfigCommand.get());
                    assertEquals(new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "31/12/2025"),
                            capturedCacheCommand.get());
                    assertEquals(new RefreshReferenceDateResult("JP", "31/12/2025", true, true), result);
                })
                .verifyComplete();
    }

    @Test
    void configUpsertFailure_failsSafely() {
        ApimReferenceDateRefreshPort apimPort = accountEnv -> Mono.just(LocalDate.of(2025, 12, 31));
        ReferenceDateConfigPort configPort = command -> Mono.error(new IllegalStateException("config upsert failed"));
        AtomicBoolean cacheCalled = new AtomicBoolean(false);
        ReferenceDateCacheUpdatePort cachePort = command -> {
            cacheCalled.set(true);
            return Mono.empty();
        };

        RefreshReferenceDateUseCase service = new RefreshReferenceDateService(apimPort, configPort, cachePort, "ngtpa");

        StepVerifier.create(service.execute(new RefreshReferenceDateCommand("JP")))
                .expectErrorSatisfies(error -> {
                    assertEquals(IllegalStateException.class, error.getClass());
                    assertEquals("config upsert failed", error.getMessage());
                    assertEquals(false, cacheCalled.get());
                })
                .verify();
    }

    @Test
    void redisFailure_returnsPartialSuccessAndLogsSanitizedWarning() {
        ApimReferenceDateRefreshPort apimPort = accountEnv -> Mono.just(LocalDate.of(2025, 12, 31));
        ReferenceDateConfigPort configPort = command -> Mono.empty();
        ReferenceDateCacheUpdatePort cachePort = command -> Mono.error(new CacheException(
                "Cache set operation failed",
                new RuntimeException("lettuce write failed redis://secret-host:6379")));

        var logger = (Logger) LoggerFactory.getLogger(RefreshReferenceDateService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            RefreshReferenceDateUseCase service = new RefreshReferenceDateService(apimPort, configPort, cachePort, "ngtpa");

            StepVerifier.create(service.execute(new RefreshReferenceDateCommand("JP")))
                    .assertNext(result -> assertEquals(
                            new RefreshReferenceDateResult("JP", "31/12/2025", true, false),
                            result))
                    .verifyComplete();

            boolean hasSanitizedWarn = appender.list.stream()
                    .anyMatch(event -> event.getLevel() == Level.WARN
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
    void applicationContractsUseApplicationFacingTypes() throws NoSuchMethodException {
        assertEquals(String.class, RefreshReferenceDateCommand.class.getRecordComponents()[0].getType());
        assertEquals(String.class, ReferenceDateCacheUpdateCommand.class.getRecordComponents()[0].getType());
        assertEquals(String.class, ReferenceDateCacheUpdateCommand.class.getRecordComponents()[1].getType());
        assertEquals(String.class, ReferenceDateConfigUpsertCommand.class.getRecordComponents()[0].getType());
        assertEquals(String.class, ReferenceDateConfigUpsertCommand.class.getRecordComponents()[1].getType());

        assertEquals(String.class, RefreshReferenceDateResult.class.getRecordComponents()[0].getType());
        assertEquals(String.class, RefreshReferenceDateResult.class.getRecordComponents()[1].getType());
        assertEquals(boolean.class, RefreshReferenceDateResult.class.getRecordComponents()[2].getType());
        assertEquals(boolean.class, RefreshReferenceDateResult.class.getRecordComponents()[3].getType());

        ParameterizedType apimReturnType = (ParameterizedType) ApimReferenceDateRefreshPort.class
                .getMethod("fetchReferenceDate", String.class)
                .getGenericReturnType();
        assertEquals(Mono.class, apimReturnType.getRawType());
        assertEquals(LocalDate.class, apimReturnType.getActualTypeArguments()[0]);

        ParameterizedType useCaseReturnType = (ParameterizedType) RefreshReferenceDateUseCase.class
                .getMethod("execute", RefreshReferenceDateCommand.class)
                .getGenericReturnType();
        assertEquals(Mono.class, useCaseReturnType.getRawType());
        assertEquals(RefreshReferenceDateResult.class, useCaseReturnType.getActualTypeArguments()[0]);

        ParameterizedType configReturnType = (ParameterizedType) ReferenceDateConfigPort.class
            .getMethod("upsertReferenceDate", ReferenceDateConfigUpsertCommand.class)
            .getGenericReturnType();
        assertEquals(Mono.class, configReturnType.getRawType());
        assertEquals(Void.class, configReturnType.getActualTypeArguments()[0]);

        ParameterizedType cacheReturnType = (ParameterizedType) ReferenceDateCacheUpdatePort.class
            .getMethod("updateReferenceDate", ReferenceDateCacheUpdateCommand.class)
            .getGenericReturnType();
        assertEquals(Mono.class, cacheReturnType.getRawType());
        assertEquals(Void.class, cacheReturnType.getActualTypeArguments()[0]);
    }
}