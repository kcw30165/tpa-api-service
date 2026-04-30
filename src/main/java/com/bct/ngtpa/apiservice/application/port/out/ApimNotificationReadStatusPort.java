package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import reactor.core.publisher.Mono;

public interface ApimNotificationReadStatusPort {
    Mono<UpdateNotificationsReadStatusResult> updateReadStatus(UpdateNotificationsReadStatusCommand command);
}