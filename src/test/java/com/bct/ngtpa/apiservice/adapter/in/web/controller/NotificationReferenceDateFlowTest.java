package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationReadStatusWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationWebMapper;
import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.adapter.out.referencedate.NonpReferenceDateAdapter;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ConfigEntry;
import com.bct.ngtpa.apiservice.application.dto.ConfigQuery;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateConfigPort;
import com.bct.ngtpa.apiservice.application.usecase.GetNotificationsService;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.MessageType;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationReferenceDateFlowTest {

    @Test
    void notificationsEndpoint_succeedsThroughControllerUseCaseAndNonpReferenceDateAdapter_apimFallbackPath() {
        var redisRead = new AtomicBoolean(false);
        var configRead = new AtomicBoolean(false);
        var apimRead = new AtomicBoolean(false);
        var configUpdated = new AtomicBoolean(false);
        var redisUpdated = new AtomicBoolean(false);
        var noticeCommand = new AtomicReference<GetNotificationsCommand>();

        CachePort cachePort = new CachePort() {
            @Override
            public Mono<Optional<String>> get(String cacheKey) {
                redisRead.set(true);
                return Mono.just(Optional.empty());
            }

            @Override
            public Mono<Void> set(String cacheKey, String value, Duration ttl) {
                return Mono.empty();
            }

            @Override
            public Mono<Boolean> evict(String cacheKey) {
                return Mono.just(false);
            }
        };

        ConfigServicePort configServicePort = new ConfigServicePort() {
            @Override
            public Mono<List<ConfigEntry>> listConfigs(ConfigQuery query) {
                configRead.set(true);
                return Mono.just(List.of());
            }

            @Override
            public Mono<ConfigEntry> upsertConfig(com.bct.ngtpa.apiservice.application.dto.ConfigUpsertCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Mono<Void> deleteConfig(String application, String profile, String label, String configKey) {
                throw new UnsupportedOperationException();
            }
        };

        ApimReferenceDateRefreshPort referenceDateApimPort = accountEnv -> {
            apimRead.set(true);
            return Mono.just(LocalDate.of(2026, 6, 30));
        };

        ReferenceDateConfigPort referenceDateConfigPort = command -> {
            configUpdated.set(true);
            assertThat(command.configKey()).isEqualTo("reference-date.JP");
            assertThat(command.configValue()).isEqualTo("30/06/2026");
            return Mono.empty();
        };

        ReferenceDateCacheUpdatePort referenceDateCacheUpdatePort = command -> {
            redisUpdated.set(true);
            assertThat(command.cacheKey()).isEqualTo("ngtpa:reference-date:JP");
            assertThat(command.cacheValue()).isEqualTo("30/06/2026");
            return Mono.empty();
        };

        var properties = new ReferenceDateProperties();
        properties.setAccountEnv("JP");
        properties.setCacheTtlSeconds(3600);
        var environment = new MockEnvironment();
        environment.setActiveProfiles("SIT");

        var referenceDatePort = new NonpReferenceDateAdapter(
                properties,
                Optional.of(cachePort),
                "ngtpa",
                configServicePort,
                referenceDateApimPort,
                referenceDateConfigPort,
                referenceDateCacheUpdatePort,
                environment,
                Clock.fixed(LocalDate.of(2026, 6, 21).atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC")));

        CurrentPortalAccessContextResolver currentPortalAccessContextResolver = new CurrentPortalAccessContextResolver() {
            @Override
            public Mono<PortalAccessContext> current() {
                return Mono.just(new PortalAccessContext(
                        new ActorContext("USER-1", "MEMBER"),
                        new AccountContext("ACC-1", "JP", "POL-1", "CERT-1", "TRUST", "SCHEME", null, null)));
            }

            @Override
            public Mono<PortalAccessContext> currentOrEmpty() {
                return current();
            }
        };

        ApimNoticeMessagePort noticePort = command -> {
            noticeCommand.set(command);
            return Mono.just(new NotificationListResult(List.of(
                    new NoticeMessage(
                            "MSG-1",
                            "MSG-1-LONG",
                            1,
                            "GENERAL",
                            MessageType.IMPORTANT_NOTICE,
                            "Reference Date Notice",
                            "內容",
                            "Content",
                            MessageStatus.UNREAD.isRead(),
                            LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusDays(1),
                            null,
                            MessageStatus.UNREAD,
                            null,
                            null,
                            List.of()))));
        };

        var controller = new NotificationController(
                new GetNotificationsService(noticePort, currentPortalAccessContextResolver, referenceDatePort),
                command -> Mono.error(new UnsupportedOperationException()),
                new NotificationWebMapper(),
                new NotificationReadStatusWebMapper());

        WebTestClient.bindToController(controller)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications").build())
                .header("Account-Ref", "ACC-1")
                .header("Accept-Language", "en")
                .header("X-Request-Id", "REQ-123")
                .header("sid_xxx", "SID-123")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.notifications[0].msgTitle").isEqualTo("Reference Date Notice")
                .jsonPath("$.notifications[0].msgCode").isEqualTo("MSG-1-LONG");

        assertThat(redisRead.get()).isTrue();
        assertThat(configRead.get()).isTrue();
        assertThat(apimRead.get()).isTrue();
        assertThat(configUpdated.get()).isTrue();
        assertThat(redisUpdated.get()).isTrue();
        assertThat(noticeCommand.get().refDate()).isEqualTo("30/06/2026");
    }
}