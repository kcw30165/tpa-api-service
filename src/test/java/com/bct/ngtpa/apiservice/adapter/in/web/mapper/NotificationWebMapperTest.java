package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.domain.model.AudienceType;
import com.bct.ngtpa.apiservice.domain.model.Hyperlink;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.MessageType;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationWebMapperTest {

    private final NotificationWebMapper mapper = new NotificationWebMapper();

    @Test
    void toResponsePrefersMsgCodeLong() {
        var result = new NotificationListResult(
                List.of(noticeMessage("SHORT", "LONG", 1)),
                NotificationDateOptions.defaults());

        var response = mapper.toResponse(result);

        assertEquals("LONG", response.notifications().getFirst().msgCode());
    }

    @Test
    void toResponseFallsBackToMsgCodeWhenLongCodeIsNull() {
        var result = new NotificationListResult(
                List.of(noticeMessage("SHORT", null, 1)),
                NotificationDateOptions.defaults());

        var response = mapper.toResponse(result);

        assertEquals("SHORT", response.notifications().getFirst().msgCode());
    }

    @Test
    void toResponseConvertsSequenceToString() {
        var result = new NotificationListResult(
                List.of(noticeMessage("SHORT", "LONG", 42)),
                NotificationDateOptions.defaults());

        var response = mapper.toResponse(result);

        assertEquals("42", response.notifications().getFirst().sequence());
    }

    @Test
    void toResponseMapsNullSequenceToNull() {
        var result = new NotificationListResult(
                List.of(noticeMessage("SHORT", null, null)),
                NotificationDateOptions.defaults());

        var response = mapper.toResponse(result);

        assertNull(response.notifications().getFirst().sequence());
    }

    @Test
    void toResponseFormatsStartDateTimeUsingProvidedOptions() {
        var options = NotificationDateOptions.resolve("yyyy-MM-dd HH:mm", "UTC");
        var result = new NotificationListResult(
                List.of(noticeMessage("SHORT", "LONG", 1)),
                options);

        var response = mapper.toResponse(result);

        assertEquals("2026-05-04 08:30", response.notifications().getFirst().startDateTime());
    }

    @Test
    void toResponseUsesDefaultDateOptionsWhenResultDateOptionsIsNull() {
        var result = new NotificationListResult(
                List.of(noticeMessage("SHORT", "LONG", 1)),
                null);

        var response = mapper.toResponse(result);

        // default format is "dd/MM/yyyy HH:mm" at system timezone; just verify non-null and non-empty
        var startDateTime = response.notifications().getFirst().startDateTime();
        assertEquals("04/05/2026 08:30", startDateTime);
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
