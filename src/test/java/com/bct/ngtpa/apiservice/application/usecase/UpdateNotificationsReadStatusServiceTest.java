package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.MemberOwnerContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateNotificationsReadStatusServiceTest {

    private static final PortalAccessContext NOTIFICATIONS_CONTEXT = new PortalAccessContext(
            new ActorContext("userId_for_notifications", "MEMBER", "SELF"),
            new MemberOwnerContext("userId_for_notifications", "MBR"),
            new AccountContext(
                    "notifications",
                    "JP",
                    "policyNo_for_notifications",
                    "certNo_for_notifications",
                    "trustCode_for_notifications",
                    "schemeType_for_notifications",
                    TermStatus.BLANK,
                    null));

    private static final ReferenceDatePort REFERENCE_DATE_PORT =
            () -> Mono.just(LocalDate.of(2025, 10, 1));

    @Test
    void enrichesCommandFromPortalAccessContextPortAndReturnsPortResult() {
        AtomicReference<UpdateNotificationsReadStatusCommand> captured = new AtomicReference<>();
        AtomicInteger invocationCount = new AtomicInteger();
        UpdateNotificationsReadStatusResult expectedResult = new UpdateNotificationsReadStatusResult(List.of(
                new NotificationReadStatus("msgCode1", MessageStatus.READ, true)));

        ApimNotificationReadStatusPort port = command -> {
            captured.set(command);
            invocationCount.incrementAndGet();
            return Mono.just(expectedResult);
        };

        AtomicReference<String> capturedRef = new AtomicReference<>();
        PortalAccessContextPort portalAccessContextPort = accountRef -> {
            capturedRef.set(accountRef);
            return Mono.just(NOTIFICATIONS_CONTEXT);
        };

        UpdateNotificationsReadStatusService service = new UpdateNotificationsReadStatusService(port, portalAccessContextPort, REFERENCE_DATE_PORT);

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
        assertEquals("notifications", capturedRef.get());
        assertEquals("JP", captured.get().env());
        assertEquals("MBR", captured.get().mbrType());
        assertEquals(List.of("msgCode1", "msgCode2"), captured.get().notificationIds());
        // policyNo and certNo come from account context
        assertEquals("policyNo_for_notifications", captured.get().policyNo());
        assertEquals("certNo_for_notifications", captured.get().certNo());
        // userId comes from actor.actorUserId(), not from account
        assertEquals("userId_for_notifications", captured.get().userId());
        assertEquals("01/10/2025", captured.get().refDate());
        assertEquals(MessageStatus.READ, captured.get().targetStatus());
    }

    // NOTE: Actor/MemberOwner authorization check is deferred.
    // When auth-server integration is complete, an additional test should verify:
    //   - execute() succeeds when ctx.actor().actorUserId().equals(ctx.memberOwner().memberUserId())
    //   - execute() signals an error (e.g. UnauthorizedException) when they differ
    // This rule is not enforced here because the current transitional adapter
    // always populates actor and memberOwner from the same config profile,
    // making the distinction meaningless until real tokens are available.
}
