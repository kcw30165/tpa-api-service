package com.bct.ngtpa.apiservice.application.dto;

import com.bct.ngtpa.apiservice.domain.model.MessageStatus;

import java.util.List;

public record UpdateNotificationsReadStatusCommand(
        String accountEnv,
        String mbrType,
        List<String> notificationIds,
        String policyNo,
        String certNo,
        String userId,
        String refDate,
        MessageStatus targetStatus,
        String accountRef
) {
    public UpdateNotificationsReadStatusCommand(
            String accountEnv,
            String mbrType,
            List<String> notificationIds,
            String policyNo,
            String certNo,
            String userId,
            String refDate,
            MessageStatus targetStatus) {
        this(accountEnv, mbrType, notificationIds, policyNo, certNo, userId, refDate, targetStatus, null);
    }

    public UpdateNotificationsReadStatusCommand {
        notificationIds = notificationIds == null ? null : List.copyOf(notificationIds);
    }
}