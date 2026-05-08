package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdateNotificationsReadStatusRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.NotificationListResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdateNotificationsReadStatusResponse;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdateNotificationsReadStatusUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private final GetNotificationsUseCase getNotificationsUseCase;
        private final UpdateNotificationsReadStatusUseCase updateNotificationsReadStatusUseCase;

    @GetMapping(value = "/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<NotificationListResponse> getNotifications(
            @RequestParam(value = "env", required = false) String env,
            @RequestParam(value = "mbrType", required = false) String mbrType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "dateFormat", required = false) String dateFormat,
            @RequestParam(value = "timezone", required = false) String timezone) {
        return getNotificationsUseCase
                .execute(new GetNotificationsCommand(
                        env,
                        mbrType,
                        page,
                        size,
                        dateFormat,
                        timezone,
                        null,
                        null,
                        null,
                        null))
                .map(NotificationListResponse::from);
    }

    @PatchMapping(
            value = "/notifications",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UpdateNotificationsReadStatusResponse> updateNotificationsReadStatus(
            @Valid @RequestBody UpdateNotificationsReadStatusRequest request) {
        return updateNotificationsReadStatusUseCase
                .execute(new UpdateNotificationsReadStatusCommand(
                        request.env(),
                        request.mbrType(),
                        request.notificationId(),
                        null,
                        null,
                        null,
                        null,
                        null))
                .map(UpdateNotificationsReadStatusResponse::from);
    }
}
