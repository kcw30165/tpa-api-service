package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.NotificationDto;
import com.bct.ngtpa.apiservice.adapter.in.web.response.NotificationListResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.NotificationListResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationWebMapper {

  public MutationResponse<NotificationListResultResponse> toResponse(
      NotificationListResult result, String language) {
    var dateOptions =
        result.dateOptions() != null ? result.dateOptions() : NotificationDateOptions.defaults();
    boolean chinese = RequestLanguageResolver.isZhHk(language);

    return MutationResponse.success(
        ApiStatus.SUCCESS,
        new NotificationListResultResponse(
            new NotificationListResponse(
                result.notifications().stream()
                    .map(message -> toDto(message, dateOptions, chinese))
                    .toList())),
        List.of());
  }

  NotificationDto toDto(
      NoticeMessage message, NotificationDateOptions dateOptions, boolean chinese) {
    return new NotificationDto(
        message.msgCodeLong() != null ? message.msgCodeLong() : message.msgCode(),
        message.seq() != null ? message.seq().toString() : null,
        message.category(),
        content(message, chinese),
        message.isRead(),
        dateOptions.format(message.startDatetime()));
  }

  private static String content(NoticeMessage message, boolean chinese) {
    return chinese && hasText(message.msgContentChi())
        ? message.msgContentChi()
        : message.msgContentEng();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
