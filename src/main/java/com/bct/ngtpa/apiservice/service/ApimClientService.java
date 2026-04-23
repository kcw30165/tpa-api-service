package com.bct.ngtpa.apiservice.service;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId;

import com.bct.ngtpa.apiservice.dto.MessageBoardMessage;
import com.bct.ngtpa.apiservice.dto.MessageBoardResponse;
import com.bct.ngtpa.apiservice.dto.apim.APIMResponsePayload;
import com.bct.ngtpa.apiservice.dto.apim.GetMessageBoardDataItem;
import com.bct.ngtpa.apiservice.dto.apim.GetMessageBoardMessageItem;
import com.bct.ngtpa.apiservice.dto.apim.GetMessageBoardRequest;
import com.bct.ngtpa.apiservice.dto.apim.GetMessageBoardResponse;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ApimClientService {
    private static final String MESSAGE_BOARD_API_NAME = "TRPGetMsgBoard";
    private final ObjectMapper objectMapper;
    private final WebClient apimWebClient;
    private final ApimCertificateService apimCertificateService;
    private final ApimPayloadCryptoService apimPayloadCryptoService;

    public ApimClientService(
            WebClient apimWebClient,
            ApimCertificateService apimCertificateService,
            ApimPayloadCryptoService apimPayloadCryptoService,
            ObjectMapper objectMapper) {
        this.apimWebClient = apimWebClient;
        this.apimCertificateService = apimCertificateService;
        this.apimPayloadCryptoService = apimPayloadCryptoService;
        this.objectMapper = objectMapper;
    }

    public Mono<MessageBoardResponse> getMessageBoard(GetMessageBoardRequest getMessageBoardRequest) {
        return apimCertificateService.getBctPublicKey()
                .flatMap(publicKey -> {
                    GetMessageBoardRequest encryptedRequest = apimPayloadCryptoService.encryptRequest(
                            MESSAGE_BOARD_API_NAME,
                            getMessageBoardRequest,
                            GetMessageBoardRequest.class,
                            publicKey);
                    logEncryptedRequest(encryptedRequest);

                    return apimWebClient.post()
                            .uri(uriBuilder -> uriBuilder.path("TRPGetMsgBoard").build())
                            .attributes(clientRegistrationId("apim-client"))
                            .bodyValue(encryptedRequest)
                            .exchangeToMono(this::extractResponseBody)
                            .map(responseBody -> apimPayloadCryptoService.decryptResponse(
                                    MESSAGE_BOARD_API_NAME,
                                    responseBody,
                                    GetMessageBoardResponse.class,
                                    publicKey))
                            .map(this::toMessageBoardResponse);
                });
    }

    private Mono<String> extractResponseBody(ClientResponse response) {
        HttpStatusCode statusCode = response.statusCode();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(responseBody -> {
                    if (statusCode.isError()) {
                        log.error("APIM request failed with status={} body={}", statusCode.value(), responseBody);
                        return Mono.error(new ApimException(
                                HttpStatus.BAD_GATEWAY,
                                "APIM request failed with status=%s body=%s".formatted(
                                        statusCode.value(),
                                        responseBody)));
                    }
                    return Mono.just(responseBody);
                });
    }

    private MessageBoardResponse toMessageBoardResponse(GetMessageBoardResponse apimResponse) {
        APIMResponsePayload<GetMessageBoardDataItem> payload = apimResponse != null ? apimResponse.getResponse() : null;
        if (payload == null) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, "APIM response payload is missing.");
        }
        if (StringUtils.hasText(payload.getErrMessage())) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, payload.getErrMessage());
        }

        List<GetMessageBoardDataItem> dataItems = payload.getData();
        if (CollectionUtils.isEmpty(dataItems)) {
            return MessageBoardResponse.builder()
                    .messages(Collections.emptyList())
                    .build();
        }

        Integer page = dataItems.stream()
                .filter(Objects::nonNull)
                .map(GetMessageBoardDataItem::getPage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        Integer size = dataItems.stream()
                .filter(Objects::nonNull)
                .map(GetMessageBoardDataItem::getSize)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        List<MessageBoardMessage> messages = dataItems.stream()
                .filter(Objects::nonNull)
                .map(GetMessageBoardDataItem::getMessage)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(this::toMessageBoardMessage)
                .toList();

        return MessageBoardResponse.builder()
                .page(page)
                .size(size)
                .messages(messages)
                .build();
    }

    private MessageBoardMessage toMessageBoardMessage(GetMessageBoardMessageItem messageItem) {
        return MessageBoardMessage.builder()
                .msgCode(messageItem.getMsgCode())
                .msgCodeLong(messageItem.getMsgCodeLong())
                .seq(messageItem.getSeq())
                .msgCate(messageItem.getMsgCate())
                .msgContentChi(messageItem.getMsgContentChi())
                .msgContentEng(messageItem.getMsgContentEng())
                .startDatetime(messageItem.getStartDatetime())
                .msgStatus(messageItem.getMsgStatus())
                .build();
    }

    private void logEncryptedRequest(Object encryptedRequest) {
        try {
            log.info("APIM request body={}", objectMapper.writeValueAsString(encryptedRequest));
        } catch (Exception ex) {
            log.warn("Failed to convert request body to JSON for logging.", ex);
        }
    }

}
