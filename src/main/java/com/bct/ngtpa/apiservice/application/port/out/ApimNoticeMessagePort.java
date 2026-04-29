package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import reactor.core.publisher.Mono;

public interface ApimNoticeMessagePort {
    Mono<NotificationListResult> fetchNotifications(GetNotificationsCommand command);
}
