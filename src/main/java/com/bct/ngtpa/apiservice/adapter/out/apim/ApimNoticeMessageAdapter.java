package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardMessageItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.domain.model.AudienceType;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.MessageType;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApimNoticeMessageAdapter implements ApimNoticeMessagePort {
  private static final String API_NAME = "/ws/NGTPA/v1/TRPGetMsgBoard";
  private static final List<DateTimeFormatter> APIM_DT_FORMATTERS =
      List.of(
          DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
          DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

  private final ApimExchangeExecutor executor;

  @Override
  @LogExecution(value = "apim.fetchNotifications", logArgs = true)
  public Mono<NotificationListResult> fetchNotifications(GetNotificationsCommand command) {
    return Mono.defer(
        () -> {
          var exchange =
              new ApimExchangeRequest<>(
                  API_NAME,
                  toApimRequest(command),
                  GetMessageBoardApimRequest.class,
                  GetMessageBoardDataItem.class,
                  ApimDatabaseScope.ACCOUNT_ENV,
                  new ApimCredentialResolutionContext(command.mbrType(), command.accountEnv()),
                  ApimRetryEligibility.SAFE_BUSINESS_CALL);
          return executor.postEnvelope(exchange).map(this::toNotificationListResult);
        });
  }

  private GetMessageBoardApimRequest toApimRequest(GetNotificationsCommand command) {
    return GetMessageBoardApimRequest.builder()
        .policyNo(command.policyNo())
        .certNo(command.certNo())
        .accountEnv(command.accountEnv())
        .loginUserId(command.userId())
        .loginUserRole(command.mbrType().toApimValue())
        .refDate(command.refDate())
        .build();
  }

  private NotificationListResult toNotificationListResult(
      ApimResponseEnvelope<GetMessageBoardDataItem> response) {
    var dataItems = ApimResponseValidator.requireSuccessData(response);
    if (CollectionUtils.isEmpty(dataItems)) return new NotificationListResult(List.of());
    List<NoticeMessage> messages =
        dataItems.stream()
            .filter(Objects::nonNull)
            .map(GetMessageBoardDataItem::getMessage)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .filter(Objects::nonNull)
            .map(this::toNoticeMessage)
            .toList();
    return new NotificationListResult(messages);
  }

  private NoticeMessage toNoticeMessage(GetMessageBoardMessageItem item) {
    var status = MessageStatus.fromCode(item.getMsgStatus());
    var category = item.getMsgCate();
    return new NoticeMessage(
        item.getMsgCode(),
        item.getMsgCodeLong(),
        item.getSeq(),
        category,
        MessageType.fromCode(category),
        MessageType.titleFor(category),
        item.getMsgContentChi(),
        item.getMsgContentEng(),
        status.isRead(),
        parseDateTime(item.getStartDatetime()),
        null,
        status,
        (AudienceType) null,
        null,
        List.of());
  }

  private LocalDateTime parseDateTime(String value) {
    if (!StringUtils.hasText(value)) return null;
    var trimmed = value.trim();
    for (DateTimeFormatter formatter : APIM_DT_FORMATTERS) {
      try {
        return LocalDateTime.parse(trimmed, formatter);
      } catch (Exception ignored) {
      }
    }
    log.warn("Unable to parse APIM datetime value='{}'; treating as null.", value);
    return null;
  }
}
