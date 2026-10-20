package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.NotificationReadStatusDto;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdateNotificationsReadStatusResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdateNotificationsReadStatusResultResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationReadStatusWebMapper {

  public MutationResponse<UpdateNotificationsReadStatusResultResponse> toResponse(
      UpdateNotificationsReadStatusResult result) {
    return MutationResponse.success(
        ApiStatus.UPDATED,
        new UpdateNotificationsReadStatusResultResponse(
            new UpdateNotificationsReadStatusResponse(
                result.notifications().stream().map(this::toDto).toList())),
        List.of());
  }

  NotificationReadStatusDto toDto(NotificationReadStatus status) {
    return new NotificationReadStatusDto(status.msgCode(), status.isRead());
  }
}
