package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateNotificationReadStatusApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateNotificationReadStatusApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import com.bct.ngtpa.apiservice.exception.ApimException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ApimNotificationReadStatusAdapter implements ApimNotificationReadStatusPort {

    private static final String API_NAME = "/ws/NGTPA/v1/TRPUpdMsgRead";

    private final ApimWebClientFacade apimWebClientFacade;
    private final ApimCertificateService apimCertificateService;
    private final ApimPayloadCryptoService apimPayloadCryptoService;
    private final ApimProperties apimProperties;

    @Override
    @LogExecution(value = "apim.updateNotificationReadStatus", logArgs = true)
    public Mono<UpdateNotificationsReadStatusResult> updateReadStatus(UpdateNotificationsReadStatusCommand command) {
        if (!apimProperties.getEncryption().isEnabled()) {
                var request = apimPayloadCryptoService.encryptRequest(
                    API_NAME, toApimRequest(command), UpdateNotificationReadStatusApimRequest.class, null);
                return apimWebClientFacade.post(API_NAME, request)
                    .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                        API_NAME, body, UpdateNotificationReadStatusApimDataItem.class, null))
                    .map(response -> toResult(response, command.targetStatus()))
                    .onErrorMap(ApimCryptoException.class, ex ->
                        new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, "500", ex.getMessage()));
        }

        return apimCertificateService.getBctPublicKey()
                .flatMap(publicKey -> {
                        var request = apimPayloadCryptoService.encryptRequest(
                            API_NAME, toApimRequest(command), UpdateNotificationReadStatusApimRequest.class, publicKey);
                        return apimWebClientFacade.post(API_NAME, request)
                            .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                                API_NAME, body, UpdateNotificationReadStatusApimDataItem.class, publicKey))
                            .map(response -> toResult(response, command.targetStatus()));
                })
                .onErrorMap(ApimCryptoException.class, ex ->
                    new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, "500", ex.getMessage()));
    }

    private UpdateNotificationReadStatusApimRequest toApimRequest(UpdateNotificationsReadStatusCommand command) {
        return UpdateNotificationReadStatusApimRequest.builder()
                .certNo(command.certNo())
                .env(command.env())
                .mbrType(command.mbrType())
                .msgCodeLong(String.join(",", command.notificationIds()))
                .policyNo(command.policyNo())
                .refDate(command.refDate())
                .status(command.targetStatus() == null ? null : command.targetStatus().name())
                .userId(command.userId())
                .build();
    }

    private UpdateNotificationsReadStatusResult toResult(
            ApimResponseEnvelope<UpdateNotificationReadStatusApimDataItem> response,
            MessageStatus targetStatus) {
        var payload = response != null ? response.getResponse() : null;
        if (payload == null) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, "APIM response payload is missing.");
        }
        if (StringUtils.hasText(payload.getErrMessage())) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, payload.getErrMessage());
        }

        var dataItems = payload.getData();
        if (CollectionUtils.isEmpty(dataItems)) {
            return new UpdateNotificationsReadStatusResult(List.of());
        }

        var effectiveStatus = targetStatus == null ? MessageStatus.UNKNOWN : targetStatus;
        List<NotificationReadStatus> notifications = dataItems.stream()
                .filter(Objects::nonNull)
                .map(item -> new NotificationReadStatus(resolveMessageCode(item), effectiveStatus, item.isSuccess()))
                .toList();

        return new UpdateNotificationsReadStatusResult(notifications);
    }

    private String resolveMessageCode(UpdateNotificationReadStatusApimDataItem item) {
        if (StringUtils.hasText(item.getMsgCodeLong())) {
            return item.getMsgCodeLong();
        }
        return item.getMsgCode();
    }
}