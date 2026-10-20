package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationReadStatusWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.notification.NotificationController;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class NotificationPatchHttpContractTest {

  @Test
  void patchNotificationsReturnsMutationResponseWithPayloadUnderResultData() {
    var controller =
        new NotificationController(
            command -> Mono.just(new NotificationListResult(List.of())),
            command ->
                Mono.just(
                    new UpdateNotificationsReadStatusResult(
                        List.of(
                            new NotificationReadStatus("N-1", MessageStatus.READ, true),
                            new NotificationReadStatus("N-2", MessageStatus.READ, false)))),
            new NotificationWebMapper(),
            new NotificationReadStatusWebMapper(),
            resolver());

    WebTestClient.bindToController(controller)
        .build()
        .patch()
        .uri("/api/v1/notifications")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("notificationId", List.of("N-1", "N-2")))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.status")
        .isEqualTo("UPDATED")
        .jsonPath("$.messages")
        .isEmpty()
        .jsonPath("$.errors")
        .isEmpty()
        .jsonPath("$.result.data.notifications[0].msgCode")
        .isEqualTo("N-1")
        .jsonPath("$.result.data.notifications[0].isRead")
        .isEqualTo(true)
        .jsonPath("$.result.data.notifications[1].msgCode")
        .isEqualTo("N-2")
        .jsonPath("$.result.data.notifications[1].isRead")
        .isEqualTo(false);
  }

  private static CurrentPortalAccessContextResolver resolver() {
    PortalAccessContext context =
        new PortalAccessContext(
            new ActorContext("USER-1", ActorUserRole.ROLE_USER_MBR),
            new AccountContext(
                "ACC-1", "JP", "POL-1", "CERT-1", "TRUST", "SCHEME", TermStatus.BLANK, null));
    return new CurrentPortalAccessContextResolver() {
      public Mono<PortalAccessContext> current() {
        return Mono.just(context);
      }

      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.just(context);
      }
    };
  }
}
