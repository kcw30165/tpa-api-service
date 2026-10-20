package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationReadStatusWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationWebMapper;
import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.domain.model.AudienceType;
import com.bct.ngtpa.apiservice.domain.model.Hyperlink;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.MessageType;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationResponseSerializationContractTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void getResponseSerializesExactEnvelopeResultDataAndNotificationKeys() {
    var result = new NotificationListResult(List.of(message()), NotificationDateOptions.defaults());

    JsonNode root = JSON.valueToTree(new NotificationWebMapper().toResponse(result, "en"));

    assertThat(keys(root))
        .containsExactlyInAnyOrder("success", "status", "result", "messages", "errors");
    assertThat(keys(root.path("result"))).containsExactly("data");
    assertThat(keys(root.at("/result/data"))).containsExactly("notifications");
    assertThat(keys(root.at("/result/data/notifications/0")))
        .containsExactlyInAnyOrder(
            "msgCode", "sequence", "category", "msgTitle", "msgContent", "isRead", "startDateTime");
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/result/data/notifications/0/isRead").isBoolean()).isTrue();
  }

  @Test
  void patchResponseSerializesExactEnvelopeResultDataAndNotificationKeys() {
    var result =
        new UpdateNotificationsReadStatusResult(
            List.of(
                new NotificationReadStatus("MSG-1", MessageStatus.READ, true),
                new NotificationReadStatus("MSG-2", MessageStatus.READ, false)));

    JsonNode root = JSON.valueToTree(new NotificationReadStatusWebMapper().toResponse(result));

    assertThat(keys(root))
        .containsExactlyInAnyOrder("success", "status", "result", "messages", "errors");
    assertThat(keys(root.path("result"))).containsExactly("data");
    assertThat(keys(root.at("/result/data"))).containsExactly("notifications");
    assertThat(keys(root.at("/result/data/notifications/0")))
        .containsExactlyInAnyOrder("msgCode", "isRead");
    assertThat(root.at("/status").asText()).isEqualTo("UPDATED");
    assertThat(root.at("/messages").size()).isZero();
    assertThat(root.at("/errors").size()).isZero();
    assertThat(root.at("/result/data/notifications/0/isRead").asBoolean()).isTrue();
    assertThat(root.at("/result/data/notifications/1/isRead").asBoolean()).isFalse();
  }

  @Test
  void notificationReadStatusDtoSerializesIsReadKeyExplicitly() {
    JsonNode node = JSON.valueToTree(new NotificationReadStatusDto("MSG-1", true));

    assertThat(keys(node)).containsExactlyInAnyOrder("msgCode", "isRead");
  }

  private static List<String> keys(JsonNode node) {
    List<String> keys = new ArrayList<>();
    node.fieldNames().forEachRemaining(keys::add);
    return keys;
  }

  private static NoticeMessage message() {
    return new NoticeMessage(
        "SHORT",
        "LONG",
        1,
        "GENERAL",
        MessageType.IMPORTANT_NOTICE,
        "Title",
        "Chi",
        "Eng",
        false,
        LocalDateTime.of(2026, 5, 4, 8, 30),
        null,
        MessageStatus.UNREAD,
        (AudienceType) null,
        null,
        List.<Hyperlink>of());
  }
}
