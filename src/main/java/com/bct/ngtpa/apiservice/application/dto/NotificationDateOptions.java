package com.bct.ngtpa.apiservice.application.dto;

import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record NotificationDateOptions(ZoneId zoneId, DateTimeFormatter formatter, String dateFormat) {

    public static final String DEFAULT_DATE_FORMAT = "dd/MM/yyyy HH:mm";
    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Hong_Kong");

    public static NotificationDateOptions defaults() {
        return resolve(null, null);
    }

    public static NotificationDateOptions resolve(String dateFormat, String timezone) {
        var resolvedDateFormat = hasText(dateFormat) ? dateFormat.trim() : DEFAULT_DATE_FORMAT;
        var resolvedTimezone = hasText(timezone) ? timezone.trim() : DEFAULT_ZONE_ID.getId();

        final ZoneId zoneId;
        try {
            zoneId = ZoneId.of(resolvedTimezone);
        } catch (DateTimeException ex) {
            throw new InvalidNotificationRequestException("Invalid timezone: " + resolvedTimezone);
        }

        final DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(resolvedDateFormat);
        } catch (IllegalArgumentException ex) {
            throw new InvalidNotificationRequestException("Invalid dateFormat: " + resolvedDateFormat);
        }

        return new NotificationDateOptions(zoneId, formatter, resolvedDateFormat);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(zoneId);
    }

    public String format(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(zoneId).format(formatter);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}