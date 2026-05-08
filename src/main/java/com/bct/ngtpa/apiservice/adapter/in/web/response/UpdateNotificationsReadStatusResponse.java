package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public record UpdateNotificationsReadStatusResponse(List<NotificationReadStatusDto> notifications) {

    public UpdateNotificationsReadStatusResponse {
        notifications = notifications == null ? List.of() : List.copyOf(notifications);
    }
}