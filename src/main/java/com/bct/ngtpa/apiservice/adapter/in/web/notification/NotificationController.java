package com.bct.ngtpa.apiservice.adapter.in.web.notification;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.*;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdateNotificationsReadStatusRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {
  private final GetNotificationsUseCase getNotificationsUseCase;
  private final UpdateNotificationsReadStatusUseCase updateNotificationsReadStatusUseCase;
  private final NotificationWebMapper notificationWebMapper;
  private final NotificationReadStatusWebMapper notificationReadStatusWebMapper;
  private final CurrentPortalAccessContextResolver currentPortalAccessContextResolver;

  @GetMapping(value = "/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<NotificationListResultResponse>> getNotifications(
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size,
      @RequestParam(value = "dateFormat", required = false) String dateFormat,
      @RequestParam(value = "timezone", required = false) String timezone) {
    return currentPortalAccessContextResolver
        .current()
        .flatMap(
            ctx ->
                getNotificationsUseCase.execute(
                    new GetNotificationsCommand(
                        ctx.account().accountEnv(),
                        ctx.actor().actorUserRole(),
                        page,
                        size,
                        dateFormat,
                        timezone,
                        ctx.account().policyNo(),
                        ctx.account().certNo(),
                        ctx.actor().actorUserId(),
                        null)))
        .map(notificationWebMapper::toResponse);
  }

  @PatchMapping(
      value = "/notifications",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<UpdateNotificationsReadStatusResponse>>
      updateNotificationsReadStatus(
          @Valid @RequestBody UpdateNotificationsReadStatusRequest request) {
    return currentPortalAccessContextResolver
        .current()
        .flatMap(
            ctx ->
                updateNotificationsReadStatusUseCase.execute(
                    new UpdateNotificationsReadStatusCommand(
                        ctx.account().accountEnv(),
                        ctx.actor().actorUserRole(),
                        request.notificationId(),
                        ctx.account().policyNo(),
                        ctx.account().certNo(),
                        ctx.actor().actorUserId(),
                        null,
                        null)))
        .map(notificationReadStatusWebMapper::toResponse);
  }
}
