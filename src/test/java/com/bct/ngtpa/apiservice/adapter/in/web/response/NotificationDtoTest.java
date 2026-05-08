package com.bct.ngtpa.apiservice.adapter.in.web.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NotificationDtoTest {

    @Test
    void recordFieldsAreAccessible() {
        var dto = new NotificationDto("CODE", "1", "GENERAL", "Title", "Chi", "Eng", false, "01/01/2026 00:00");

        assertEquals("CODE", dto.msgCode());
        assertEquals("1", dto.sequence());
        assertEquals("GENERAL", dto.category());
        assertEquals("Title", dto.msgTitle());
        assertEquals("Chi", dto.msgContentChi());
        assertEquals("Eng", dto.msgContentEng());
        assertFalse(dto.isRead());
        assertEquals("01/01/2026 00:00", dto.startDateTime());
    }
}