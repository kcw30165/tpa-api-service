package com.bct.ngtpa.apiservice.application.dto;

import com.bct.ngtpa.apiservice.domain.model.MessageStatus;

import java.util.List;

public record UpdateNotificationsReadStatusCommand(
        String env,
        String mbrType,
        List<String> notificationIds,
        String policyNo,
        String certNo,
        String userId,
        String refDate,
        MessageStatus targetStatus
) {
    public UpdateNotificationsReadStatusCommand {
        notificationIds = notificationIds == null ? null : List.copyOf(notificationIds);
    }
}