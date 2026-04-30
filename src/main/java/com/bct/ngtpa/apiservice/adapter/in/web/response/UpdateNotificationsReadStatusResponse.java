package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;

import java.util.List;

public record UpdateNotificationsReadStatusResponse(List<NotificationReadStatusDto> notifications) {

    public UpdateNotificationsReadStatusResponse {
        notifications = notifications == null ? List.of() : List.copyOf(notifications);
    }

    public static UpdateNotificationsReadStatusResponse from(UpdateNotificationsReadStatusResult result) {
        return new UpdateNotificationsReadStatusResponse(
                result.notifications().stream()
                        .map(NotificationReadStatusDto::from)
                        .toList());
    }
}