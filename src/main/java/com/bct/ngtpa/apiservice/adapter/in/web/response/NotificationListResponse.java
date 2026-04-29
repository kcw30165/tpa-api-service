package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;

import java.util.List;

public record NotificationListResponse(List<NotificationDto> notifications) {

    public static NotificationListResponse from(NotificationListResult result) {
    var dateOptions = result.dateOptions() != null ? result.dateOptions() : NotificationDateOptions.defaults();

        return new NotificationListResponse(
        result.notifications().stream()
            .map(message -> NotificationDto.from(message, dateOptions))
            .toList()
        );
    }
}
