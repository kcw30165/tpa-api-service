package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationReadStatusWebMapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final NotificationReadStatusWebMapper mapper = new NotificationReadStatusWebMapper();

  @Test
  void toResponseMapsMsgCodeCorrectly() {
    var result =
        new UpdateNotificationsReadStatusResult(
            List.of(new NotificationReadStatus("code-01", MessageStatus.READ, true)));

    JsonNode root = JSON.valueToTree(mapper.toResponse(result));

    assertEquals("code-01", root.at("/result/data/notifications/0/msgCode").asText());
    assertTrue(root.at("/success").asBoolean());
    assertEquals("UPDATED", root.at("/status").asText());
    assertTrue(root.at("/messages").isArray());
    assertEquals(0, root.at("/messages").size());
    assertTrue(root.at("/errors").isArray());
    assertEquals(0, root.at("/errors").size());
  }

  @Test
  void toResponseReturnsIsReadTrueWhenStatusReadAndSuccessTrue() {
    var result =
        new UpdateNotificationsReadStatusResult(
            List.of(new NotificationReadStatus("code-01", MessageStatus.READ, true)));

    JsonNode first = notifications(result).path(0);

    assertTrue(first.at("/isRead").isBoolean());
    assertTrue(first.at("/isRead").asBoolean());
  }

  @Test
  void toResponseReturnsIsReadFalseWhenStatusReadButSuccessFalse() {
    var result =
        new UpdateNotificationsReadStatusResult(
            List.of(new NotificationReadStatus("code-01", MessageStatus.READ, false)));

    JsonNode first = notifications(result).path(0);

    assertTrue(first.at("/isRead").isBoolean());
    assertFalse(first.at("/isRead").asBoolean());
  }

  @Test
  void toResponseMapsAllNotificationsInOrder() {
    var result =
        new UpdateNotificationsReadStatusResult(
            List.of(
                new NotificationReadStatus("code-01", MessageStatus.READ, true),
                new NotificationReadStatus("code-02", MessageStatus.READ, false)));

    JsonNode notifications = notifications(result);

    assertEquals(2, notifications.size());
    assertEquals("code-01", notifications.get(0).at("/msgCode").asText());
    assertTrue(notifications.get(0).at("/isRead").asBoolean());
    assertEquals("code-02", notifications.get(1).at("/msgCode").asText());
    assertFalse(notifications.get(1).at("/isRead").asBoolean());
  }

  @Test
  void toResponseKeepsEmptyNotificationsUnderData() {
    var result = new UpdateNotificationsReadStatusResult(List.of());

    JsonNode notifications = notifications(result);

    assertTrue(notifications.isArray());
    assertEquals(0, notifications.size());
  }

  private JsonNode notifications(UpdateNotificationsReadStatusResult result) {
    JsonNode root = JSON.valueToTree(mapper.toResponse(result));
    return root.at("/result/data/notifications");
  }
}
