package com.bct.ngtpa.apiservice.adapter.in.web.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationListResponseTest {

    @Test
    void recordFieldIsAccessible() {
        var dto = new NotificationDto("CODE", "1", "GENERAL", "Title", "Chi", "Eng", false, "01/01/2026 00:00");
        var response = new NotificationListResponse(List.of(dto));

        assertEquals(1, response.notifications().size());
        assertEquals("CODE", response.notifications().getFirst().msgCode());
    }
}