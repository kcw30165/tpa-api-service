package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.GetMessageBoardCommand;
import com.bct.ngtpa.apiservice.application.dto.MessageBoardResult;
import reactor.core.publisher.Mono;

public interface GetMessageBoardUseCase {
    Mono<MessageBoardResult> execute(GetMessageBoardCommand command);
}
