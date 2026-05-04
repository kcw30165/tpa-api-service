package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NoticeMessageTest {

    @Test
    void exposesRecordComponents() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 4, 10, 0);
        LocalDateTime end = start.plusDays(2);
        List<Hyperlink> hyperlinks = List.of(new Hyperlink("Details", "https://example.test/details"));

        NoticeMessage noticeMessage = noticeMessage(start, end, hyperlinks);

        assertEquals("msg-code", noticeMessage.msgCode());
        assertEquals("msg-code-long", noticeMessage.msgCodeLong());
        assertEquals(3, noticeMessage.seq());
        assertEquals("GENERAL", noticeMessage.category());
        assertEquals(MessageType.ACTION_REQUIRED, noticeMessage.msgType());
        assertEquals("Title", noticeMessage.msgTitle());
        assertEquals("Chi", noticeMessage.msgContentChi());
        assertEquals("Eng", noticeMessage.msgContentEng());
        assertTrue(noticeMessage.isRead());
        assertEquals(start, noticeMessage.startDatetime());
        assertEquals(end, noticeMessage.endDatetime());
        assertEquals(MessageStatus.READ, noticeMessage.msgStatus());
        assertEquals(AudienceType.ALL_MEMBERS, noticeMessage.targetAudience());
        assertEquals("LOGIN", noticeMessage.triggerPoint());
        assertIterableEquals(hyperlinks, noticeMessage.hyperlinks());
    }

    @ParameterizedTest
    @MethodSource("expiredCases")
    void evaluatesExpiredState(LocalDateTime endDatetime, LocalDateTime now, boolean expectedExpired) {
        NoticeMessage noticeMessage = noticeMessage(LocalDateTime.of(2026, 5, 1, 8, 0), endDatetime, List.of());

        assertEquals(expectedExpired, noticeMessage.isExpired(now));
    }

    @ParameterizedTest
    @MethodSource("visibleCases")
    void evaluatesVisibleState(LocalDateTime startDatetime, LocalDateTime endDatetime, LocalDateTime now,
            boolean expectedVisible) {
        NoticeMessage noticeMessage = noticeMessage(startDatetime, endDatetime, List.of());

        assertEquals(expectedVisible, noticeMessage.isVisible(now));
    }

    @Test
    void evaluatesVisibilityAndExpiryAgainstCurrentTime() {
        NoticeMessage activeMessage = noticeMessage(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), List.of());
        NoticeMessage expiredMessage = noticeMessage(LocalDateTime.now().minusDays(3), LocalDateTime.now().minusHours(1), List.of());

        assertTrue(activeMessage.isVisible());
        assertFalse(activeMessage.isExpired());
        assertTrue(expiredMessage.isExpired());
        assertFalse(expiredMessage.isVisible());
    }

    @Test
    void exposesAudienceTypeValues() {
        assertIterableEquals(List.of(AudienceType.ALL_MEMBERS, AudienceType.TRIGGER_POINT),
                List.of(AudienceType.values()));
    }

    private static Stream<Arguments> expiredCases() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 4, 12, 0);
        return Stream.of(
                Arguments.of(null, now, false),
                Arguments.of(now.plusMinutes(1), now, false),
                Arguments.of(now, now, false),
                Arguments.of(now.minusMinutes(1), now, true),
                Arguments.of(now.minusMinutes(1), null, false));
    }

    private static Stream<Arguments> visibleCases() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 4, 12, 0);
        return Stream.of(
                Arguments.of(null, now.plusDays(1), now, false),
                Arguments.of(now.minusDays(1), now.plusDays(1), null, false),
                Arguments.of(now.minusDays(2), now.minusMinutes(1), now, false),
                Arguments.of(now.plusMinutes(1), now.plusDays(1), now, false),
                Arguments.of(now, now.plusDays(1), now, true),
                Arguments.of(now.minusMinutes(1), null, now, true));
    }

    private static NoticeMessage noticeMessage(LocalDateTime startDatetime, LocalDateTime endDatetime,
            List<Hyperlink> hyperlinks) {
        return new NoticeMessage(
                "msg-code",
                "msg-code-long",
                3,
                "GENERAL",
                MessageType.ACTION_REQUIRED,
                "Title",
                "Chi",
                "Eng",
                true,
                startDatetime,
                endDatetime,
                MessageStatus.READ,
                AudienceType.ALL_MEMBERS,
                "LOGIN",
                hyperlinks);
    }
}