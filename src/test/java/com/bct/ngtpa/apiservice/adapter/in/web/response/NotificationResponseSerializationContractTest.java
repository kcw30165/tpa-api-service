package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationWebMapper;
import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.domain.model.AudienceType;
import com.bct.ngtpa.apiservice.domain.model.Hyperlink;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.MessageType;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
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

    JsonNode root = JSON.valueToTree(new NotificationWebMapper().toResponse(result));

    assertThat(keys(root))
        .containsExactlyInAnyOrder("success", "status", "result", "messages", "errors");
    assertThat(keys(root.path("result"))).containsExactly("data");
    assertThat(keys(root.at("/result/data"))).containsExactly("notifications");
    assertThat(keys(root.at("/result/data/notifications/0")))
        .containsExactlyInAnyOrder(
            "msgCode",
            "sequence",
            "category",
            "msgTitle",
            "msgContentChi",
            "msgContentEng",
            "isRead",
            "startDateTime");
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/result/data/notifications/0/isRead").isBoolean()).isTrue();
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
