package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationListResponseTest {

  @Test
  void recordFieldIsAccessible() {
    var dto = new NotificationDto("CODE", "1", "GENERAL", "Content", false, "01/01/2026 00:00");
    var response = new NotificationListResponse(List.of(dto));

    assertEquals(1, response.notifications().size());
    assertEquals("CODE", response.notifications().getFirst().msgCode());
  }
}
