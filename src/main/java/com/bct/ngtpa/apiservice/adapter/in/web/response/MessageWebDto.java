package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;

import java.time.format.DateTimeFormatter;

public record MessageWebDto(
        String msgCode,
        String msgCodeLong,
        Integer seq,
        String msgType,
        String msgContentChi,
        String msgContentEng,
        String startDatetime,
        String msgStatus
) {
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static MessageWebDto from(NoticeMessage message) {
        return new MessageWebDto(
                message.msgCode(),
                message.msgCodeLong(),
                message.seq(),
                message.msgType() != null ? message.msgType().name() : null,
                message.msgContentChi(),
                message.msgContentEng(),
                message.startDatetime() != null
                        ? message.startDatetime().format(DISPLAY_FORMATTER) : null,
                message.msgStatus() != null ? message.msgStatus().name() : null
        );
    }
}
