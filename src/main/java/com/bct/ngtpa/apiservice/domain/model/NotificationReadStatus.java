package com.bct.ngtpa.apiservice.domain.model;

public record NotificationReadStatus(String msgCode, MessageStatus status, boolean success) {

    public NotificationReadStatus {
        status = status == null ? MessageStatus.UNKNOWN : status;
    }

    public boolean isRead() {
        return success && status.isRead();
    }
}