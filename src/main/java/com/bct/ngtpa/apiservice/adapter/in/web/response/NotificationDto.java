package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.format.DateTimeFormatter;

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
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static NotificationDto from(NoticeMessage message) {
        return new NotificationDto(
                message.msgCode(),
                message.seq() != null ? message.seq().toString() : null,
                message.msgType() != null ? message.msgType().name() : null,
                message.msgTitle(),
                message.msgContentChi(),
                message.msgContentEng(),
                message.isRead(),
                message.startDatetime() != null
                        ? message.startDatetime().format(DISPLAY_FORMATTER) : null
        );
    }
}
