package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberContext;
import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.domain.model.AudienceType;
import com.bct.ngtpa.apiservice.domain.model.Hyperlink;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.MessageType;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetNotificationsServiceTest {

    private static final MemberContext NOTIFICATIONS_CONTEXT = new MemberContext(
            "policyNo_for_notifications",
            "certNo_for_notifications",
            "userId_for_notifications",
            "trustCode_for_notifications",
            "schemeType_for_notifications");

    private static MemberContextPort memberContextPort() {
        return purpose -> Mono.just(NOTIFICATIONS_CONTEXT);
    }

    @Test
    void filtersFutureAndMissingStartDatesAndKeepsDefaultDateOptions() {
        NotificationDateOptions defaults = NotificationDateOptions.defaults();
        LocalDateTime now = LocalDateTime.now(defaults.zoneId());

        ApimNoticeMessagePort port = command -> Mono.just(new NotificationListResult(List.of(
                notification("DOC_AVAIL", 3, now.minusMinutes(15), false),
                notification("ACT_REQ", 1, now, false),
                notification("IMP_NOTE", 2, now.plusMinutes(15), true),
                notification("MKT_UPD", 4, null, false)
        )));

        GetNotificationsService service = new GetNotificationsService(port, memberContextPort());

        NotificationListResult result = service.execute(new GetNotificationsCommand(
                "DEV", "MBR", 1, 20, null, null, null, null, null, null)).block();

        assertEquals(NotificationDateOptions.DEFAULT_DATE_FORMAT, result.dateOptions().dateFormat());
        assertEquals(NotificationDateOptions.DEFAULT_ZONE_ID, result.dateOptions().zoneId());
        assertEquals(List.of(1, 3), result.notifications().stream().map(NoticeMessage::seq).toList());
    }

    @Test
    void usesCustomDateOptions() {
        ApimNoticeMessagePort port = command -> Mono.just(new NotificationListResult(List.of()));
        GetNotificationsService service = new GetNotificationsService(port, memberContextPort());

        NotificationListResult result = service.execute(new GetNotificationsCommand(
                "DEV", "MBR", null, null, "yyyy-MM-dd HH:mm", "Europe/London", null, null, null, null)).block();

        assertEquals("yyyy-MM-dd HH:mm", result.dateOptions().dateFormat());
        assertEquals("Europe/London", result.dateOptions().zoneId().getId());
    }

    @Test
    void rejectsInvalidDateOptions() {
        ApimNoticeMessagePort port = command -> Mono.just(new NotificationListResult(List.of()));
        GetNotificationsService service = new GetNotificationsService(port, memberContextPort());

        assertThrows(InvalidNotificationRequestException.class, () -> service.execute(new GetNotificationsCommand(
                "DEV", "MBR", null, null, "bad-[", null, null, null, null, null)).block());

        assertThrows(InvalidNotificationRequestException.class, () -> service.execute(new GetNotificationsCommand(
                "DEV", "MBR", null, null, null, "Mars/Olympus", null, null, null, null)).block());
    }

    @Test
    void resolvesMemberContextWithNotificationsPurpose() {
        AtomicReference<MemberContextPurpose> capturedPurpose = new AtomicReference<>();
        AtomicReference<GetNotificationsCommand> capturedCommand = new AtomicReference<>();

        MemberContextPort capturingPort = purpose -> {
            capturedPurpose.set(purpose);
            return Mono.just(NOTIFICATIONS_CONTEXT);
        };

        ApimNoticeMessagePort noticePort = command -> {
            capturedCommand.set(command);
            return Mono.just(new NotificationListResult(List.of()));
        };

        GetNotificationsService service = new GetNotificationsService(noticePort, capturingPort);
        service.execute(new GetNotificationsCommand("DEV", "MBR", 1, 20, null, null, null, null, null, null)).block();

        assertEquals(MemberContextPurpose.NOTIFICATIONS, capturedPurpose.get());
        assertEquals("policyNo_for_notifications", capturedCommand.get().policyNo());
        assertEquals("certNo_for_notifications", capturedCommand.get().certNo());
        assertEquals("userId_for_notifications", capturedCommand.get().userId());
    }

    private NoticeMessage notification(String category, Integer seq, LocalDateTime startDateTime, boolean isRead) {
        return new NoticeMessage(
                "SHORT-" + seq,
                "LONG-" + seq,
                seq,
                category,
                MessageType.fromCode(category),
                MessageType.titleFor(category),
                "chi",
                "eng",
                isRead,
                startDateTime,
                null,
                isRead ? MessageStatus.READ : MessageStatus.UNREAD,
                (AudienceType) null,
                null,
                List.<Hyperlink>of()
        );
    }
}
