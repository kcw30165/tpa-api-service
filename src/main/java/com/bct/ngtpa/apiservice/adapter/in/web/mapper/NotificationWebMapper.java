package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.NotificationDto;
import com.bct.ngtpa.apiservice.adapter.in.web.response.NotificationListResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.NotificationListResultResponse;
import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationWebMapper {

  public MutationResponse<NotificationListResultResponse> toResponse(
      NotificationListResult result) {
    var dateOptions =
        result.dateOptions() != null ? result.dateOptions() : NotificationDateOptions.defaults();

    return MutationResponse.success(
        ApiStatus.SUCCESS,
        new NotificationListResultResponse(
            new NotificationListResponse(
                result.notifications().stream()
                    .map(message -> toDto(message, dateOptions))
                    .toList())),
        List.of());
  }

  NotificationDto toDto(NoticeMessage message, NotificationDateOptions dateOptions) {
    return new NotificationDto(
        message.msgCodeLong() != null ? message.msgCodeLong() : message.msgCode(),
        message.seq() != null ? message.seq().toString() : null,
        message.category(),
        message.msgTitle(),
        message.msgContentChi(),
        message.msgContentEng(),
        message.isRead(),
        dateOptions.format(message.startDatetime()));
  }
}
