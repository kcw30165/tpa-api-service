package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import com.fasterxml.jackson.annotation.JsonProperty;

public record NotificationDto(
        String msgCode,
        String sequence,
        String category,
        String msgTitle,
        String msgContentChi,
        String msgContentEng,
        @JsonProperty("isRead") boolean isRead,
        String startDateTime
) {
    public static NotificationDto from(NoticeMessage message, NotificationDateOptions dateOptions) {
        return new NotificationDto(
                message.msgCodeLong() != null ? message.msgCodeLong() : message.msgCode(),
                message.seq() != null ? message.seq().toString() : null,
                message.category(),
                message.msgTitle(),
                message.msgContentChi(),
                message.msgContentEng(),
                message.isRead(),
                dateOptions.format(message.startDatetime())
        );
    }
}
