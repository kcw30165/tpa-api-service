package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationReadStatusWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.notification.NotificationController;
import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.adapter.out.referencedate.ReferenceDateAdapter;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
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

class NotificationReferenceDateFlowTest {
  @Test
  void notificationsEndpointUsesRedisThenHongKongDateWithoutAutomaticReferenceDateRefresh() {
    var redisRead = new AtomicBoolean(false);
    var cacheWriteCalled = new AtomicBoolean(false);
    var noticeCommand = new AtomicReference<GetNotificationsCommand>();

    CachePort cachePort =
        new CachePort() {
          @Override
          public Mono<Optional<String>> get(String cacheKey) {
            redisRead.set(true);
            assertThat(cacheKey).isEqualTo("ngtpa:reference-date:JP");
            return Mono.just(Optional.empty());
          }

          @Override
          public Mono<Void> set(String cacheKey, String value, Duration ttl) {
            cacheWriteCalled.set(true);
            return Mono.empty();
          }

          @Override
          public Mono<Boolean> evict(String cacheKey) {
            return Mono.just(false);
          }
        };

    var properties = new ReferenceDateProperties();
    properties.setAccountEnv("JP");
    properties.setCacheTtlSeconds(3600);
    var environment = new MockEnvironment();
    environment.setActiveProfiles("SIT");
    var referenceDatePort =
        new ReferenceDateAdapter(
            properties,
            Optional.of(cachePort),
            "ngtpa",
            environment,
            Clock.fixed(
                LocalDate.of(2026, 6, 21).atStartOfDay(ZoneId.of("UTC")).toInstant(),
                ZoneId.of("UTC")));

    CurrentPortalAccessContextResolver currentPortalAccessContextResolver =
        new CurrentPortalAccessContextResolver() {
          @Override
          public Mono<PortalAccessContext> current() {
            return Mono.just(
                new PortalAccessContext(
                    new ActorContext("USER-1", ActorUserRole.ROLE_USER_MBR),
                    new AccountContext(
                        "ACC-1", "JP", "POL-1", "CERT-1", "TRUST", "SCHEME", null, null)));
          }

          @Override
          public Mono<PortalAccessContext> currentOrEmpty() {
            return current();
          }
        };

    ApimNoticeMessagePort noticePort =
        command -> {
          noticeCommand.set(command);
          return Mono.just(
              new NotificationListResult(
                  List.of(
                      new NoticeMessage(
                          "MSG-1",
                          "MSG-1-LONG",
                          1,
                          "GENERAL",
                          MessageType.IMPORTANT_NOTICE,
                          "Reference Date Notice",
                          "Content zh",
                          "Content",
                          MessageStatus.UNREAD.isRead(),
                          LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusDays(1),
                          null,
                          MessageStatus.UNREAD,
                          null,
                          null,
                          List.of()))));
        };

    var controller =
        new NotificationController(
            new GetNotificationsService(noticePort, referenceDatePort),
            command -> Mono.error(new UnsupportedOperationException()),
            new NotificationWebMapper(),
            new NotificationReadStatusWebMapper(),
            currentPortalAccessContextResolver);

    WebTestClient.bindToController(controller)
        .build()
        .get()
        .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications").build())
        .header("Account-Ref", "ACC-1")
        .header("Accept-Language", "en")
        .header("X-Request-Id", "REQ-123")
        .header("X-Authenticated-Session-Id", "SID-123")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.status")
        .isEqualTo("SUCCESS")
        .jsonPath("$.messages")
        .isEmpty()
        .jsonPath("$.errors")
        .isEmpty()
        .jsonPath("$.result.data.notifications[0].msgTitle")
        .isEqualTo("Reference Date Notice")
        .jsonPath("$.result.data.notifications[0].msgCode")
        .isEqualTo("MSG-1-LONG")
        .jsonPath("$.result.data.notifications[0].msgContent")
        .isEqualTo("Content")
        .jsonPath("$.result.data.notifications[0].msgContentChi")
        .doesNotExist()
        .jsonPath("$.result.data.notifications[0].msgContentEng")
        .doesNotExist();

    assertThat(redisRead.get()).isTrue();
    assertThat(cacheWriteCalled.get()).isFalse();
    assertThat(noticeCommand.get()).isNotNull();
    assertThat(noticeCommand.get().accountEnv()).isEqualTo("JP");
    assertThat(noticeCommand.get().refDate()).isEqualTo("21/06/2026");
  }
}
