package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;

public record NotificationReadStatusDto(String msgCode, boolean isRead) {

    public static NotificationReadStatusDto from(NotificationReadStatus status) {
        return new NotificationReadStatusDto(status.msgCode(), status.isRead());
    }
}