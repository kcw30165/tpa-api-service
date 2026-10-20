package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class NotificationDtoTest {

  @Test
  void recordFieldsAreAccessible() {
    var dto =
        new NotificationDto("CODE", "1", "GENERAL", "Title", "Content", false, "01/01/2026 00:00");

    assertEquals("CODE", dto.msgCode());
    assertEquals("1", dto.sequence());
    assertEquals("GENERAL", dto.category());
    assertEquals("Title", dto.msgTitle());
    assertEquals("Content", dto.msgContent());
    assertFalse(dto.isRead());
    assertEquals("01/01/2026 00:00", dto.startDateTime());
  }
}
