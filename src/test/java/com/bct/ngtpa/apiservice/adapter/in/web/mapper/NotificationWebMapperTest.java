package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationWebMapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final NotificationWebMapper mapper = new NotificationWebMapper();

  @Test
  void toResponsePrefersMsgCodeLong() {
    var result =
        new NotificationListResult(
            List.of(noticeMessage("SHORT", "LONG", 1)), NotificationDateOptions.defaults());

    var first = firstNotification(result);

    assertEquals("LONG", first.at("/msgCode").asText());
  }

  @Test
  void toResponseFallsBackToMsgCodeWhenLongCodeIsNull() {
    var result =
        new NotificationListResult(
            List.of(noticeMessage("SHORT", null, 1)), NotificationDateOptions.defaults());

    var first = firstNotification(result);

    assertEquals("SHORT", first.at("/msgCode").asText());
  }

  @Test
  void toResponseConvertsSequenceToString() {
    var result =
        new NotificationListResult(
            List.of(noticeMessage("SHORT", "LONG", 42)), NotificationDateOptions.defaults());

    var first = firstNotification(result);

    assertEquals("42", first.at("/sequence").asText());
    assertTrue(first.at("/sequence").isTextual());
  }

  @Test
  void toResponseMapsNullSequenceToNull() {
    var result =
        new NotificationListResult(
            List.of(noticeMessage("SHORT", null, null)), NotificationDateOptions.defaults());

    var first = firstNotification(result);

    assertTrue(first.at("/sequence").isNull());
  }

  @Test
  void toResponseFormatsStartDateTimeUsingProvidedOptions() {
    var options = NotificationDateOptions.resolve("yyyy-MM-dd HH:mm", "UTC");
    var result = new NotificationListResult(List.of(noticeMessage("SHORT", "LONG", 1)), options);

    var first = firstNotification(result);

    assertEquals("2026-05-04 08:30", first.at("/startDateTime").asText());
  }

  @Test
  void toResponseUsesDefaultDateOptionsWhenResultDateOptionsIsNull() {
    var result = new NotificationListResult(List.of(noticeMessage("SHORT", "LONG", 1)), null);

    var first = firstNotification(result);

    // default format is "dd/MM/yyyy HH:mm" at system timezone; just verify non-null and non-empty
    assertEquals("04/05/2026 08:30", first.at("/startDateTime").asText());
  }

  @Test
  void toResponseWrapsNotificationsInSuccessEnvelope() {
    var result =
        new NotificationListResult(
            List.of(noticeMessage("SHORT", "LONG", 1)), NotificationDateOptions.defaults());

    JsonNode root = JSON.valueToTree(mapper.toResponse(result));

    assertTrue(root.at("/success").asBoolean());
    assertEquals("SUCCESS", root.at("/status").asText());
    assertTrue(root.at("/messages").isArray());
    assertEquals(0, root.at("/messages").size());
    assertTrue(root.at("/errors").isArray());
    assertEquals(0, root.at("/errors").size());
    assertEquals(1, root.at("/result/data/notifications").size());
  }

  @Test
  void toResponseKeepsEmptyNotificationsUnderData() {
    var result = new NotificationListResult(List.of(), NotificationDateOptions.defaults());

    JsonNode root = JSON.valueToTree(mapper.toResponse(result));
    JsonNode notifications = root.at("/result/data/notifications");

    assertTrue(notifications.isArray());
    assertEquals(0, notifications.size());
  }

  private JsonNode firstNotification(NotificationListResult result) {
    JsonNode root = JSON.valueToTree(mapper.toResponse(result));
    return root.at("/result/data/notifications/0");
  }

  private static NoticeMessage noticeMessage(String msgCode, String msgCodeLong, Integer seq) {
    return new NoticeMessage(
        msgCode,
        msgCodeLong,
        seq,
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
