package com.bct.ngtpa.apiservice.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateNotificationsReadStatusDtoTest {

    @Test
    void commandKeepsNotificationIdsNullWhenInputIsNull() {
        UpdateNotificationsReadStatusCommand command = new UpdateNotificationsReadStatusCommand(
                "SIT", "EMP", null, "P1", "C1", "U1", "04/05/2026", MessageStatus.READ);

        assertEquals(null, command.notificationIds());
    }

    @Test
    void commandDefensivelyCopiesNotificationIds() {
        List<String> ids = new ArrayList<>(List.of("n1", "n2"));

        UpdateNotificationsReadStatusCommand command = new UpdateNotificationsReadStatusCommand(
                "SIT", "EMP", ids, "P1", "C1", "U1", "04/05/2026", MessageStatus.READ);
        ids.add("n3");

        assertEquals(List.of("n1", "n2"), command.notificationIds());
        assertThrows(UnsupportedOperationException.class, () -> command.notificationIds().add("n4"));
    }

    @Test
    void resultNormalizesNullNotificationsToEmptyList() {
        UpdateNotificationsReadStatusResult result = new UpdateNotificationsReadStatusResult(null);

        assertEquals(List.of(), result.notifications());
    }

    @Test
    void resultDefensivelyCopiesNotifications() {
        List<NotificationReadStatus> notifications = new ArrayList<>(List.of(
                new NotificationReadStatus("msg-1", MessageStatus.READ, true)));

        UpdateNotificationsReadStatusResult result = new UpdateNotificationsReadStatusResult(notifications);
        notifications.clear();

        assertEquals(1, result.notifications().size());
        assertThrows(UnsupportedOperationException.class, () -> result.notifications().add(
                new NotificationReadStatus("msg-2", MessageStatus.UNREAD, true)));
    }
}