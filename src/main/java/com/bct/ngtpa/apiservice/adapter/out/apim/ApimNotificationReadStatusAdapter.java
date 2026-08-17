package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateNotificationReadStatusApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateNotificationReadStatusApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ApimNotificationReadStatusAdapter implements ApimNotificationReadStatusPort {
    private static final String API_NAME = "/ws/NGTPA/v1/TRPUpdMsgRead";
    private final ApimExchangeExecutor executor;

    @Override
    @LogExecution(value = "apim.updateNotificationReadStatus", logArgs = true)
    public Mono<UpdateNotificationsReadStatusResult> updateReadStatus(UpdateNotificationsReadStatusCommand command) {
        return Mono.defer(() -> {
            var exchange = new ApimExchangeRequest<>(
                    API_NAME,
                    toApimRequest(command),
                    UpdateNotificationReadStatusApimRequest.class,
                    UpdateNotificationReadStatusApimDataItem.class,
                    ApimDatabaseScope.ACCOUNT_ENV,
                    new ApimCredentialResolutionContext(command.mbrType(), command.accountEnv()),
                    ApimRetryEligibility.IDEMPOTENT_MUTATION);
            return executor.postEnvelope(exchange).map(response -> toResult(response, command.targetStatus()));
        });
    }

    private UpdateNotificationReadStatusApimRequest toApimRequest(UpdateNotificationsReadStatusCommand command) {
        return UpdateNotificationReadStatusApimRequest.builder()
                .policyNo(command.policyNo()).certNo(command.certNo()).loginUserId(command.userId())
                .loginUserRole(command.mbrType().toApimValue()).refDate(command.refDate())
                .accountEnv(command.accountEnv()).msgCodeLong(String.join(",", command.notificationIds())).build();
    }

    private UpdateNotificationsReadStatusResult toResult(
            ApimResponseEnvelope<UpdateNotificationReadStatusApimDataItem> response, MessageStatus targetStatus) {
        var dataItems = ApimResponseValidator.requireSuccessData(response);
        if (CollectionUtils.isEmpty(dataItems)) return new UpdateNotificationsReadStatusResult(List.of());
        var effectiveStatus = targetStatus == null ? MessageStatus.UNKNOWN : targetStatus;
        List<NotificationReadStatus> notifications = dataItems.stream().filter(Objects::nonNull)
                .map(item -> new NotificationReadStatus(resolveMessageCode(item), effectiveStatus, item.isSuccess()))
                .toList();
        return new UpdateNotificationsReadStatusResult(notifications);
    }

    private String resolveMessageCode(UpdateNotificationReadStatusApimDataItem item) {
        return StringUtils.hasText(item.getMsgCodeLong()) ? item.getMsgCodeLong() : item.getMsgCode();
    }
}
