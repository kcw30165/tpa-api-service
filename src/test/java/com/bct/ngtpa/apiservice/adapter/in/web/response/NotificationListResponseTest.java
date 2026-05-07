package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.MessageType;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationListResponseTest {

    @Test
    void fromUsesDefaultDateOptionsWhenResultDoesNotProvideAny() {
        NotificationListResult result = new NotificationListResult(List.of(noticeMessage()), null);

        NotificationListResponse response = NotificationListResponse.from(result);

        assertEquals(1, response.notifications().size());
        assertEquals("04/05/2026 08:30", response.notifications().getFirst().startDateTime());
    }

    @Test
    void fromUsesProvidedDateOptionsWhenAvailable() {
        NotificationDateOptions options = NotificationDateOptions.resolve("yyyy-MM-dd", "UTC");
        NotificationListResult result = new NotificationListResult(List.of(noticeMessage()), options);

        NotificationListResponse response = NotificationListResponse.from(result);

        assertEquals("2026-05-04", response.notifications().getFirst().startDateTime());
    }

    private static NoticeMessage noticeMessage() {
        return new NoticeMessage(
                "SHORT",
                null,
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
                null,
                null,
                List.of());
    }
}