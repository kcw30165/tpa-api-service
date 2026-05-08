package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.MemberContext;
import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateNotificationsReadStatusServiceTest {

    private static final MemberContext NOTIFICATIONS_CONTEXT = new MemberContext(
            "policyNo_for_notifications",
            "certNo_for_notifications",
            "userId_for_notifications",
            "trustCode_for_notifications",
            "schemeType_for_notifications");

    @Test
    void enrichesCommandFromMemberContextPortAndReturnsPortResult() {
        AtomicReference<UpdateNotificationsReadStatusCommand> captured = new AtomicReference<>();
        AtomicInteger invocationCount = new AtomicInteger();
        UpdateNotificationsReadStatusResult expectedResult = new UpdateNotificationsReadStatusResult(List.of(
                new NotificationReadStatus("msgCode1", MessageStatus.READ, true)));

        ApimNotificationReadStatusPort port = command -> {
            captured.set(command);
            invocationCount.incrementAndGet();
            return Mono.just(expectedResult);
        };

        AtomicReference<MemberContextPurpose> capturedPurpose = new AtomicReference<>();
        MemberContextPort memberContextPort = purpose -> {
            capturedPurpose.set(purpose);
            return Mono.just(NOTIFICATIONS_CONTEXT);
        };

        UpdateNotificationsReadStatusService service = new UpdateNotificationsReadStatusService(port, memberContextPort);

        UpdateNotificationsReadStatusResult result = service.execute(new UpdateNotificationsReadStatusCommand(
                "DEV",
                "MBR",
                List.of("msgCode1", "msgCode2"),
                null,
                null,
                null,
                null,
                null)).block();

        assertEquals(expectedResult, result);
        assertEquals(1, invocationCount.get());
        assertEquals(MemberContextPurpose.NOTIFICATIONS, capturedPurpose.get());
        assertEquals("DEV", captured.get().env());
        assertEquals("MBR", captured.get().mbrType());
        assertEquals(List.of("msgCode1", "msgCode2"), captured.get().notificationIds());
        assertEquals("policyNo_for_notifications", captured.get().policyNo());
        assertEquals("certNo_for_notifications", captured.get().certNo());
        assertEquals("userId_for_notifications", captured.get().userId());
        assertEquals("01/10/2025", captured.get().refDate());
        assertEquals(MessageStatus.READ, captured.get().targetStatus());
    }
}
