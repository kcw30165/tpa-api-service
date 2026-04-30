package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.APIMResponsePayload;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateNotificationReadStatusApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateNotificationReadStatusApimEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateNotificationReadStatusApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateNotificationReadStatusApimResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.config.ApimProperties;
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
    public Mono<UpdateNotificationsReadStatusResult> updateReadStatus(UpdateNotificationsReadStatusCommand command) {
        if (!apimProperties.getEncryption().isEnabled()) {
            var request = apimPayloadCryptoService.encryptRequest(
                    API_NAME, toApimRequest(command), UpdateNotificationReadStatusApimRequest.class, null);
            return apimWebClientFacade.post(API_NAME, request)
                    .map(body -> apimPayloadCryptoService.decryptResponse(
                            API_NAME, body, UpdateNotificationReadStatusApimResponse.class, null))
                    .map(response -> toResult(response, command.targetStatus()));
        }

        return apimCertificateService.getBctPublicKey()
                .flatMap(publicKey -> {
                    var request = apimPayloadCryptoService.encryptRequest(
                            API_NAME, toApimRequest(command), UpdateNotificationReadStatusApimRequest.class, publicKey);
                    return apimWebClientFacade.post(API_NAME, request)
                            .map(body -> apimPayloadCryptoService.decryptResponse(
                                    API_NAME, body, UpdateNotificationReadStatusApimResponse.class, publicKey))
                            .map(response -> toResult(response, command.targetStatus()));
                });
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
            UpdateNotificationReadStatusApimResponse response,
            MessageStatus targetStatus) {
        var outerPayload = response != null ? response.getResponse() : null;
        if (outerPayload == null) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, "APIM response payload is missing.");
        }
        if (StringUtils.hasText(outerPayload.getErrMessage())) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, outerPayload.getErrMessage());
        }

        APIMResponsePayload<UpdateNotificationReadStatusApimDataItem> innerPayload = outerPayload.getResponse();
        if (innerPayload == null) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, "APIM response data payload is missing.");
        }
        if (StringUtils.hasText(innerPayload.getErrMessage())) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, innerPayload.getErrMessage());
        }
        if (CollectionUtils.isEmpty(innerPayload.getData())) {
            return new UpdateNotificationsReadStatusResult(List.of());
        }

        var effectiveStatus = targetStatus == null ? MessageStatus.UNKNOWN : targetStatus;
        List<NotificationReadStatus> notifications = innerPayload.getData().stream()
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