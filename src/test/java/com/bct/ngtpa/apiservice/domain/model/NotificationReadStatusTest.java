package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NotificationReadStatusTest {

    @Test
    void defaultsNullStatusToUnknown() {
        NotificationReadStatus status = new NotificationReadStatus("msg-1", null, true);

        assertSame(MessageStatus.UNKNOWN, status.status());
        assertFalse(status.isRead());
    }

    @Test
    void isReadRequiresSuccessAndReadStatus() {
        assertTrue(new NotificationReadStatus("msg-1", MessageStatus.READ, true).isRead());
        assertFalse(new NotificationReadStatus("msg-1", MessageStatus.READ, false).isRead());
        assertFalse(new NotificationReadStatus("msg-1", MessageStatus.UNREAD, true).isRead());
    }
}