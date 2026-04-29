package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.response.NotificationListResponse;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private final GetNotificationsUseCase getNotificationsUseCase;

    @GetMapping(value = "/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<NotificationListResponse> getNotifications(
            @RequestParam("environment") String environment,
            @RequestParam("memberType") String memberType) {
        return getNotificationsUseCase
                .execute(new GetNotificationsCommand(environment, memberType, null, null, null, null))
                .map(NotificationListResponse::from);
    }
}
