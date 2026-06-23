package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateCacheUpdateCommand;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NonpReferenceDateAdapterTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDate.of(2026, 6, 21).atTime(16, 30).atZone(UTC).toInstant(),
            UTC);

    @Test
    void prodDeploymentEnv_returnsHongKongSystemDateWithoutCallingRedisOrApim() {
        var cachePort = mock(CachePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        var adapter = adapter("JP", "PROD", Optional.of(cachePort), apimPort, cacheUpdatePort);

        StepVerifier.create(adapter.resolveReferenceDate("JP"))
                .expectNext(LocalDate.of(2026, 6, 22))
                .verifyComplete();

        verifyNoInteractions(cachePort, apimPort, cacheUpdatePort);
    }

    @Test
    void drDeploymentEnv_returnsHongKongSystemDateWithoutCallingRedisOrApim() {
        var cachePort = mock(CachePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        var adapter = adapter("JP", "DR", Optional.of(cachePort), apimPort, cacheUpdatePort);

        StepVerifier.create(adapter.resolveReferenceDate("JP"))
                .expectNext(LocalDate.of(2026, 6, 22))
                .verifyComplete();

        verifyNoInteractions(cachePort, apimPort, cacheUpdatePort);
    }

    @Test
    void redisValidDate_returnsRedisValueWithoutCallingApimOrWritingRedis() {
        var cachePort = mock(CachePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        when(cachePort.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.of("22/06/2026")));

        var adapter = adapter("JP", "SIT", Optional.of(cachePort), apimPort, cacheUpdatePort);

        StepVerifier.create(adapter.resolveReferenceDate("JP"))
                .expectNext(LocalDate.of(2026, 6, 22))
                .verifyComplete();

        verify(cachePort).get("ngtpa:reference-date:JP");
        verifyNoInteractions(apimPort, cacheUpdatePort);
    }

    @Test
    void redisMiss_apimValid_updatesRedisAndReturnsApimDate() {
        var cachePort = mock(CachePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        when(cachePort.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.empty()));
        when(apimPort.fetchReferenceDate("JP")).thenReturn(Mono.just(LocalDate.of(2026, 6, 30)));
        when(cacheUpdatePort
                .updateReferenceDate(new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "30/06/2026")))
                .thenReturn(Mono.empty());

        var adapter = adapter("JP", "SIT", Optional.of(cachePort), apimPort, cacheUpdatePort);

        StepVerifier.create(adapter.resolveReferenceDate("JP"))
                .expectNext(LocalDate.of(2026, 6, 30))
                .verifyComplete();

        verify(cachePort).get("ngtpa:reference-date:JP");
        verify(apimPort).fetchReferenceDate("JP");
        verify(cacheUpdatePort)
                .updateReferenceDate(new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "30/06/2026"));
    }

    @Test
    void redisBlankValue_fallsBackToApim() {
        var cachePort = mock(CachePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        when(cachePort.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.of("   ")));
        when(apimPort.fetchReferenceDate("JP")).thenReturn(Mono.just(LocalDate.of(2026, 6, 22)));
        when(cacheUpdatePort
                .updateReferenceDate(new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "22/06/2026")))
                .thenReturn(Mono.empty());

        var adapter = adapter("JP", "SIT", Optional.of(cachePort), apimPort, cacheUpdatePort);

        StepVerifier.create(adapter.resolveReferenceDate("JP"))
                .expectNext(LocalDate.of(2026, 6, 22))
                .verifyComplete();

        verify(apimPort).fetchReferenceDate("JP");
        verify(cacheUpdatePort)
                .updateReferenceDate(new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "22/06/2026"));
    }

    @Test
    void redisInvalidDate_logsSanitizedWarningAndFallsBackToApim() {
        var cachePort = mock(CachePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        when(cachePort.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.of("2026-06-22")));
        when(apimPort.fetchReferenceDate("JP")).thenReturn(Mono.just(LocalDate.of(2026, 6, 22)));
        when(cacheUpdatePort
                .updateReferenceDate(new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "22/06/2026")))
                .thenReturn(Mono.empty());

        var logger = (Logger) LoggerFactory.getLogger(NonpReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var adapter = adapter("JP", "SIT", Optional.of(cachePort), apimPort, cacheUpdatePort);

            StepVerifier.create(adapter.resolveReferenceDate("JP"))
                    .expectNext(LocalDate.of(2026, 6, 22))
                    .verifyComplete();

            verify(apimPort).fetchReferenceDate("JP");
            assertThat(appender.list)
                    .anyMatch(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("reference-date")
                            && !event.getFormattedMessage().contains("2026-06-22"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void redisReadError_logsSanitizedWarningAndFallsBackToApim() {
        var cachePort = mock(CachePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        when(cachePort.get("ngtpa:reference-date:JP")).thenReturn(Mono.error(new CacheException(
                "Cache get operation failed",
                new RuntimeException("redis://secret-host:6379"))));
        when(apimPort.fetchReferenceDate("JP")).thenReturn(Mono.just(LocalDate.of(2026, 7, 1)));
        when(cacheUpdatePort
                .updateReferenceDate(new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "01/07/2026")))
                .thenReturn(Mono.empty());

        var logger = (Logger) LoggerFactory.getLogger(NonpReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var adapter = adapter("JP", "SIT", Optional.of(cachePort), apimPort, cacheUpdatePort);

            StepVerifier.create(adapter.resolveReferenceDate("JP"))
                    .expectNext(LocalDate.of(2026, 7, 1))
                    .verifyComplete();

            assertThat(appender.list)
                    .anyMatch(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("reference-date")
                            && !event.getFormattedMessage().contains("secret-host")
                            && !event.getFormattedMessage().contains("6379"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void apimEmpty_fallsBackToHongKongSystemDateWithoutWrites() {
        var cachePort = mock(CachePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        when(cachePort.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.empty()));
        when(apimPort.fetchReferenceDate("JP")).thenReturn(Mono.empty());

        var adapter = adapter("JP", "SIT", Optional.of(cachePort), apimPort, cacheUpdatePort);

        StepVerifier.create(adapter.resolveReferenceDate("JP"))
                .expectNext(LocalDate.of(2026, 6, 22))
                .verifyComplete();

        verifyNoInteractions(cacheUpdatePort);
    }

    @Test
    void apimReadError_logsSanitizedWarningAndFallsBackToHongKongSystemDateWithoutWrites() {
        var cachePort = mock(CachePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        when(cachePort.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.empty()));
        when(apimPort.fetchReferenceDate("JP"))
                .thenReturn(Mono.error(new RuntimeException("apim error with token secret-token-value")));

        var logger = (Logger) LoggerFactory.getLogger(NonpReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var adapter = adapter("JP", "SIT", Optional.of(cachePort), apimPort, cacheUpdatePort);

            StepVerifier.create(adapter.resolveReferenceDate("JP"))
                    .expectNext(LocalDate.of(2026, 6, 22))
                    .verifyComplete();

            verifyNoInteractions(cacheUpdatePort);
            assertThat(appender.list)
                    .anyMatch(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("reference-date")
                            && !event.getFormattedMessage().contains("secret-token-value"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void redisUpdateFailureAfterApimSuccess_logsSanitizedWarningAndReturnsApimDate() {
        var cachePort = mock(CachePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        when(cachePort.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.empty()));
        when(apimPort.fetchReferenceDate("JP")).thenReturn(Mono.just(LocalDate.of(2026, 7, 1)));
        when(cacheUpdatePort
                .updateReferenceDate(new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "01/07/2026")))
                .thenReturn(Mono.error(new CacheException(
                        "Cache set operation failed",
                        new RuntimeException("redis://secret-host:6379"))));

        var logger = (Logger) LoggerFactory.getLogger(NonpReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var adapter = adapter("JP", "SIT", Optional.of(cachePort), apimPort, cacheUpdatePort);

            StepVerifier.create(adapter.resolveReferenceDate("JP"))
                    .expectNext(LocalDate.of(2026, 7, 1))
                    .verifyComplete();

            assertThat(appender.list)
                    .anyMatch(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("reference-date")
                            && !event.getFormattedMessage().contains("secret-host")
                            && !event.getFormattedMessage().contains("6379"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private NonpReferenceDateAdapter adapter(
            String accountEnv,
            String deploymentEnv,
            Optional<CachePort> cachePort,
            ApimReferenceDateRefreshPort apimPort,
            ReferenceDateCacheUpdatePort cacheUpdatePort) {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv(accountEnv);
        properties.setCacheTtlSeconds(3600);
        var environment = new MockEnvironment();
        environment.setActiveProfiles(deploymentEnv);
        return new NonpReferenceDateAdapter(
                properties,
                cachePort,
                "ngtpa",
                apimPort,
                cacheUpdatePort,
                environment,
                FIXED_CLOCK);
    }
}