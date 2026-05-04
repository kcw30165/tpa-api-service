package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.MessageType;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationDtoTest {

    @Test
    void fromPrefersLongMessageCodeAndFormatsSequence() {
        NoticeMessage message = noticeMessage("SHORT", "LONG", 42);
        NotificationDateOptions options = NotificationDateOptions.resolve("yyyy-MM-dd HH:mm", "UTC");

        NotificationDto dto = NotificationDto.from(message, options);

        assertEquals("LONG", dto.msgCode());
        assertEquals("42", dto.sequence());
        assertEquals("2026-05-04 08:30", dto.startDateTime());
    }

    @Test
    void fromFallsBackToShortMessageCodeWhenLongCodeIsMissing() {
        NoticeMessage message = noticeMessage("SHORT", null, null);

        NotificationDto dto = NotificationDto.from(message, NotificationDateOptions.defaults());

        assertEquals("SHORT", dto.msgCode());
        assertNull(dto.sequence());
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
                true,
                LocalDateTime.of(2026, 5, 4, 8, 30),
                null,
                MessageStatus.READ,
                null,
                null,
                List.of());
    }
}