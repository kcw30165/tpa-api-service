package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateNotificationsReadStatusServiceTest {

    @Test
    void enrichesCommandWithHardcodedFieldsAndReturnsPortResult() {
        AtomicReference<UpdateNotificationsReadStatusCommand> captured = new AtomicReference<>();
        AtomicInteger invocationCount = new AtomicInteger();
        UpdateNotificationsReadStatusResult expectedResult = new UpdateNotificationsReadStatusResult(List.of(
                new NotificationReadStatus("msgCode1", MessageStatus.READ, true)));

        ApimNotificationReadStatusPort port = command -> {
            captured.set(command);
            invocationCount.incrementAndGet();
            return Mono.just(expectedResult);
        };

        UpdateNotificationsReadStatusService service = new UpdateNotificationsReadStatusService(port);

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
        assertEquals("DEV", captured.get().env());
        assertEquals("MBR", captured.get().mbrType());
        assertEquals(List.of("msgCode1", "msgCode2"), captured.get().notificationIds());
        assertEquals("00000000118", captured.get().policyNo());
        assertEquals("2", captured.get().certNo());
        assertEquals("C402400A", captured.get().userId());
        assertEquals("01/01/2024", captured.get().refDate());
        assertEquals(MessageStatus.READ, captured.get().targetStatus());
    }
}