package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import reactor.core.publisher.Mono;

public interface UpdateNotificationsReadStatusUseCase {
    Mono<UpdateNotificationsReadStatusResult> execute(UpdateNotificationsReadStatusCommand command);
}