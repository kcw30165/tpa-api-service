package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.dto.ConfigEntry;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateCacheUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateConfigUpsertCommand;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateConfigPort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
    void prodDeploymentEnv_returnsHongKongSystemDateWithoutCallingRedisConfigServiceOrApim() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var configPort = mock(ReferenceDateConfigPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        var adapter = adapter("JP", "PROD", Optional.of(cachePort), configServicePort, apimPort, configPort, cacheUpdatePort);

        StepVerifier.create(adapter.resolveReferenceDate())
                .expectNext(LocalDate.of(2026, 6, 22))
                .verifyComplete();

        verifyNoInteractions(cachePort, configServicePort, apimPort, configPort, cacheUpdatePort);
    }

    @Test
    void redisMissAndConfigMiss_apimValid_updatesConfigThenRedisAndReturnsApimDate() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var configPort = mock(ReferenceDateConfigPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        when(cachePort.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.empty()));
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of()));
        when(apimPort.fetchReferenceDate("JP")).thenReturn(Mono.just(LocalDate.of(2026, 6, 30)));
        when(configPort.upsertReferenceDate(new ReferenceDateConfigUpsertCommand("reference-date.JP", "30/06/2026")))
                .thenReturn(Mono.empty());
        when(cacheUpdatePort.updateReferenceDate(new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "30/06/2026")))
                .thenReturn(Mono.empty());

        var adapter = adapter("JP", "SIT", Optional.of(cachePort), configServicePort, apimPort, configPort, cacheUpdatePort);

        StepVerifier.create(adapter.resolveReferenceDate())
                .expectNext(LocalDate.of(2026, 6, 30))
                .verifyComplete();

        verify(cachePort).get("ngtpa:reference-date:JP");
        verify(configServicePort).listConfigs(any());
        verify(apimPort).fetchReferenceDate("JP");
        verify(configPort).upsertReferenceDate(new ReferenceDateConfigUpsertCommand("reference-date.JP", "30/06/2026"));
        verify(cacheUpdatePort).updateReferenceDate(new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "30/06/2026"));
    }

    @Test
    void configServiceValid_redisWriteFails_logsWarningAndStillReturnsConfigServiceDate() {
        var cachePort = mock(CachePort.class);
        var configServicePort = mock(ConfigServicePort.class);
        var apimPort = mock(ApimReferenceDateRefreshPort.class);
        var configPort = mock(ReferenceDateConfigPort.class);
        var cacheUpdatePort = mock(ReferenceDateCacheUpdatePort.class);

        when(cachePort.get("ngtpa:reference-date:JP")).thenReturn(Mono.just(Optional.empty()));
        when(configServicePort.listConfigs(any())).thenReturn(Mono.just(List.of(
                new ConfigEntry(null, null, null, "reference-date.JP", "29/02/2024"))));
        when(cacheUpdatePort.updateReferenceDate(new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "29/02/2024")))
                .thenReturn(Mono.error(new CacheException("Cache set operation failed", new RuntimeException("redis://secret-host:6379"))));

        var logger = (Logger) LoggerFactory.getLogger(NonpReferenceDateAdapter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var adapter = adapter("JP", "SIT", Optional.of(cachePort), configServicePort, apimPort, configPort, cacheUpdatePort);

            StepVerifier.create(adapter.resolveReferenceDate())
                    .expectNext(LocalDate.of(2024, 2, 29))
                    .verifyComplete();

            verify(cacheUpdatePort).updateReferenceDate(new ReferenceDateCacheUpdateCommand("ngtpa:reference-date:JP", "29/02/2024"));
            verify(apimPort, never()).fetchReferenceDate(any());
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
            ConfigServicePort configServicePort,
            ApimReferenceDateRefreshPort apimPort,
            ReferenceDateConfigPort configPort,
            ReferenceDateCacheUpdatePort cacheUpdatePort) {
        var properties = new ReferenceDateProperties();
        properties.setAccountEnv(accountEnv);
        var environment = new MockEnvironment();
        environment.setActiveProfiles(deploymentEnv);
        return new NonpReferenceDateAdapter(
                properties,
                cachePort,
                "ngtpa",
                configServicePort,
                apimPort,
                configPort,
                cacheUpdatePort,
                environment,
                FIXED_CLOCK);
    }
}