package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.GetMessageBoardCommand;
import com.bct.ngtpa.apiservice.application.dto.MessageBoardResult;
import reactor.core.publisher.Mono;

public interface ApimNoticeMessagePort {
    Mono<MessageBoardResult> fetchMessages(GetMessageBoardCommand command);
}
