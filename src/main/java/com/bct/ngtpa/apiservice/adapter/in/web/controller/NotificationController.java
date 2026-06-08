package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationReadStatusWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdateNotificationsReadStatusRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.NotificationListResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdateNotificationsReadStatusResponse;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdateNotificationsReadStatusUseCase;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
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
import reactor.util.context.ContextView;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private final GetNotificationsUseCase getNotificationsUseCase;
    private final UpdateNotificationsReadStatusUseCase updateNotificationsReadStatusUseCase;
    private final NotificationWebMapper notificationWebMapper;
    private final NotificationReadStatusWebMapper notificationReadStatusWebMapper;

    @GetMapping(value = "/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<NotificationListResponse> getNotifications(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "dateFormat", required = false) String dateFormat,
            @RequestParam(value = "timezone", required = false) String timezone) {
        return Mono.deferContextual(contextView -> getNotificationsUseCase
                .execute(new GetNotificationsCommand(
                        null,
                        null,
                        page,
                        size,
                        dateFormat,
                        timezone,
                        null,
                        null,
                        null,
                        null,
                        resolveRequiredAccountRef(contextView)))
                .map(notificationWebMapper::toResponse));
    }

    @PatchMapping(
            value = "/notifications",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<MutationResponse<UpdateNotificationsReadStatusResponse>> updateNotificationsReadStatus(
            @Valid @RequestBody UpdateNotificationsReadStatusRequest request) {
        return Mono.deferContextual(contextView -> updateNotificationsReadStatusUseCase
                .execute(new UpdateNotificationsReadStatusCommand(
                        null,
                        null,
                        request.notificationId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        resolveRequiredAccountRef(contextView)))
                .map(notificationReadStatusWebMapper::toResponse));
    }

    private String resolveRequiredAccountRef(ContextView contextView) {
        RequestHeaderContext requestHeaderContext = contextView.getOrDefault(
                RequestHeaderContextKeys.CONTEXT_KEY,
                null);
        if (requestHeaderContext == null || requestHeaderContext.accountRef() == null) {
            throw new PortalAccessContextResolutionException(
                    ErrorCodes.MEMBER_CONTEXT_INVALID,
                    "Missing Account-Ref header for selected-account API");
        }
        return requestHeaderContext.accountRef();
    }
}
