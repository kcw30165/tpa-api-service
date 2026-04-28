package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.application.dto.MessageBoardResult;

import java.util.List;

public record MessageBoardWebResponse(
        Integer page,
        Integer size,
        List<MessageWebDto> messages
) {
    public static MessageBoardWebResponse from(MessageBoardResult result) {
        return new MessageBoardWebResponse(
                result.page(),
                result.size(),
                result.messages().stream().map(MessageWebDto::from).toList()
        );
    }
}
