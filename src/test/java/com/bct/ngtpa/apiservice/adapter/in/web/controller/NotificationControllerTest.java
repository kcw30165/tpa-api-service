package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationReadStatusWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.notification.NotificationController;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdateNotificationsReadStatusRequest;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdateNotificationsReadStatusUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class NotificationControllerTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void getNotificationsPassesQueryOnlyCommandWithoutAccountRef() {
    AtomicReference<GetNotificationsCommand> captured = new AtomicReference<>();
    GetNotificationsUseCase getUseCase =
        command -> {
          captured.set(command);
          return Mono.just(new NotificationListResult(List.of()));
        };
    UpdateNotificationsReadStatusUseCase updateUseCase =
        command -> Mono.just(new UpdateNotificationsReadStatusResult(List.of()));

    var actual =
        new NotificationController(
                getUseCase,
                updateUseCase,
                new NotificationWebMapper(),
                mock(NotificationReadStatusWebMapper.class),
                resolver())
            .getNotifications(2, 50, "yyyy-MM-dd", "Asia/Hong_Kong", "en")
            .block();

    var node = JSON.valueToTree(actual);
    assertTrue(node.at("/success").asBoolean());
    assertTrue(node.at("/result/data/notifications").isArray());
    assertEquals(0, node.at("/result/data/notifications").size());
    assertEquals(2, captured.get().page());
    assertEquals(50, captured.get().size());
    assertEquals("yyyy-MM-dd", captured.get().dateFormat());
    assertEquals("Asia/Hong_Kong", captured.get().timezone());
  }

  @Test
  void updateNotificationsPassesNotificationIdsOnlyCommandWithoutAccountRef() {
    AtomicReference<UpdateNotificationsReadStatusCommand> captured = new AtomicReference<>();
    GetNotificationsUseCase getUseCase =
        command -> Mono.just(new NotificationListResult(List.of()));
    UpdateNotificationsReadStatusUseCase updateUseCase =
        command -> {
          captured.set(command);
          return Mono.just(
              new UpdateNotificationsReadStatusResult(
                  List.of(new NotificationReadStatus("N-1", MessageStatus.READ, true))));
        };

    var actual =
        new NotificationController(
                getUseCase,
                updateUseCase,
                mock(NotificationWebMapper.class),
                new NotificationReadStatusWebMapper(),
                resolver())
            .updateNotificationsReadStatus(new UpdateNotificationsReadStatusRequest(List.of("N-1")))
            .block();

    var node = JSON.valueToTree(actual);
    assertTrue(node.at("/success").asBoolean());
    assertEquals("UPDATED", node.at("/status").asText());
    assertEquals("N-1", node.at("/result/data/notifications/0/msgCode").asText());
    assertTrue(node.at("/result/data/notifications/0/isRead").asBoolean());
    assertEquals(List.of("N-1"), captured.get().notificationIds());
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
