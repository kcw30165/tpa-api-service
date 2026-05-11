package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardMessageItem;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import com.bct.ngtpa.apiservice.domain.model.AudienceType;
import com.bct.ngtpa.apiservice.domain.model.Hyperlink;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.MessageType;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import com.bct.ngtpa.apiservice.exception.ApimException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApimNoticeMessageAdapter implements ApimNoticeMessagePort {

    private static final String API_NAME = "/ws/NGTPA/v1/TRPGetMsgBoard";

    /**
     * Assumed APIM datetime format based on BRD section 6.1 (dd/mm/yyyy hh:mm:ss).
     * To be confirmed with the APIM/Progress team.
     */
        private static final List<DateTimeFormatter> APIM_DT_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        );

    private final ApimWebClientFacade apimWebClientFacade;
    private final ApimCertificateService apimCertificateService;
    private final ApimPayloadCryptoService apimPayloadCryptoService;
    private final ApimProperties apimProperties;

    @Override
    @LogExecution(value = "apim.fetchNotifications", logArgs = true)
    public Mono<NotificationListResult> fetchNotifications(GetNotificationsCommand command) {
        if (!apimProperties.getEncryption().isEnabled()) {
            var request = apimPayloadCryptoService.encryptRequest(
                    API_NAME, toApimRequest(command), GetMessageBoardApimRequest.class, null);
            return apimWebClientFacade.post(API_NAME, request)
                .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                    API_NAME, body, GetMessageBoardDataItem.class, null))
                .map(this::toNotificationListResult)
                .onErrorMap(ApimCryptoException.class, ex ->
                    new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, "500", ex.getMessage()));
        }

        return apimCertificateService.getBctPublicKey()
                .flatMap(publicKey -> {
                    var request = apimPayloadCryptoService.encryptRequest(
                            API_NAME, toApimRequest(command), GetMessageBoardApimRequest.class, publicKey);
                    return apimWebClientFacade.post(API_NAME, request)
                        .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                            API_NAME, body, GetMessageBoardDataItem.class, publicKey))
                        .map(this::toNotificationListResult);
                })
                .onErrorMap(ApimCryptoException.class, ex ->
                    new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, "500", ex.getMessage()));
    }

    private GetMessageBoardApimRequest toApimRequest(GetNotificationsCommand command) {
        return GetMessageBoardApimRequest.builder()
                .policyNo(command.policyNo())
                .certNo(command.certNo())
                .userId(command.userId())
                .refDate(command.refDate())
                .env(command.env())
                .mbrType(command.mbrType())
                .build();
    }

    private NotificationListResult toNotificationListResult(ApimResponseEnvelope<GetMessageBoardDataItem> response) {
        var payload = response != null ? response.getResponse() : null;
        if (payload == null) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, "APIM response payload is missing.");
        }
        if (StringUtils.hasText(payload.getErrMessage())) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, payload.getErrMessage());
        }

        var dataItems = payload.getData();
        if (CollectionUtils.isEmpty(dataItems)) {
            return new NotificationListResult(List.of());
        }

        List<NoticeMessage> messages = dataItems.stream()
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
                null,               // endDatetime: TBC — not yet returned by APIM
                status,
                (AudienceType) null, // targetAudience: TBC — not yet returned by APIM
                null,               // triggerPoint:  TBC — not yet returned by APIM
                List.of()           // hyperlinks:    TBC — not yet returned by APIM
        );
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        var trimmedValue = value.trim();
        for (DateTimeFormatter formatter : APIM_DT_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmedValue, formatter);
            } catch (Exception ignored) {
                // Try the next APIM format.
            }
        }

        log.warn("Unable to parse APIM datetime value='{}'; treating as null.", value);
        return null;
    }
}

