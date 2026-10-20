package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationListResultResponseTest {

  @Test
  void keepsDataUnchanged() {
    var data = new NotificationListResponse(List.of());

    assertSame(data, new NotificationListResultResponse(data).data());
  }

  @Test
  void rejectsNullData() {
    var thrown =
        assertThrows(NullPointerException.class, () -> new NotificationListResultResponse(null));

    assertEquals("data must not be null", thrown.getMessage());
  }
}
