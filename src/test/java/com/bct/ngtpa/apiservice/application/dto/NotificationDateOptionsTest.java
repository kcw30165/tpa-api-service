package com.bct.ngtpa.apiservice.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class NotificationDateOptionsTest {

    @Test
    void resolveRejectsInvalidTimezone() {
        InvalidNotificationRequestException exception = assertThrows(
                InvalidNotificationRequestException.class,
                () -> NotificationDateOptions.resolve("yyyy-MM-dd", "Mars/Base"));

        assertEquals("Invalid timezone: Mars/Base", exception.getMessage());
    }

    @Test
    void resolveRejectsInvalidDateFormat() {
        InvalidNotificationRequestException exception = assertThrows(
                InvalidNotificationRequestException.class,
                () -> NotificationDateOptions.resolve("[invalid", ZoneId.of("UTC").getId()));

        assertEquals("Invalid dateFormat: [invalid", exception.getMessage());
    }

    @Test
    void formatReturnsNullWhenInputIsNull() {
        NotificationDateOptions options = NotificationDateOptions.resolve("yyyy-MM-dd", "UTC");

        assertNull(options.format(null));
    }

    @Test
    void resolveTrimsInputsBeforeFormatting() {
        NotificationDateOptions options = NotificationDateOptions.resolve(" yyyy-MM-dd HH:mm ", " UTC ");

        assertEquals("2026-05-04 10:15", options.format(LocalDateTime.of(2026, 5, 4, 10, 15)));
    }
}