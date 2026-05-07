package com.bct.ngtpa.apiservice.application.dto;

import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;

import java.util.List;

public record UpdateNotificationsReadStatusResult(List<NotificationReadStatus> notifications) {

    public UpdateNotificationsReadStatusResult {
        notifications = notifications == null ? List.of() : List.copyOf(notifications);
    }
}