package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.response.NotificationReadStatusDto;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdateNotificationsReadStatusResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import org.springframework.stereotype.Component;

@Component
public class NotificationReadStatusWebMapper {

    public UpdateNotificationsReadStatusResponse toResponse(UpdateNotificationsReadStatusResult result) {
        return new UpdateNotificationsReadStatusResponse(
                result.notifications().stream()
                        .map(this::toDto)
                        .toList());
    }

    NotificationReadStatusDto toDto(NotificationReadStatus status) {
        return new NotificationReadStatusDto(status.msgCode(), status.isRead());
    }
}
