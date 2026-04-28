package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import reactor.core.publisher.Mono;

public interface GetNotificationsUseCase {
    Mono<NotificationListResult> execute(GetNotificationsCommand command);
}
