package com.bct.ngtpa.apiservice.adapter.out.sms;

import com.bct.ngtpa.apiservice.adapter.out.sms.config.SmsProperties;
import com.bct.ngtpa.apiservice.adapter.out.sms.dto.SmsSendSingleRequest;
import com.bct.ngtpa.apiservice.application.dto.message.SenderResult;
import com.bct.ngtpa.apiservice.application.dto.message.SenderStatus;
import com.bct.ngtpa.apiservice.application.dto.message.SmsMessage;
import com.bct.ngtpa.apiservice.application.port.out.SmsSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
public class SmsSenderAdapter implements SmsSenderPort {

    private static final String SMS_PROVIDER_FAILURE_MESSAGE = "SMS provider request failed";
    private static final String SMS_CONFIGURATION_FAILURE_MESSAGE = "SMS adapter configuration is incomplete";

    private final WebClient webClient;
    private final SmsProperties smsProperties;

    public SmsSenderAdapter(WebClient webClient, SmsProperties smsProperties) {
        this.webClient = webClient;
        this.smsProperties = smsProperties;
    }

    @Override
    public Mono<SenderResult> sendSms(SmsMessage message) {
        SenderResult configurationFailure = validateConfiguration();
        if (configurationFailure != null) {
            log.warn(
                    "SMS adapter configuration incomplete path={} phoneMasked={} contentLogged=false",
                    smsProperties.getSendSinglePath(),
                    maskPhone(message == null ? null : message.recipient()));
            return Mono.just(configurationFailure);
        }

        if (message == null) {
            return Mono.error(new IllegalArgumentException("message must not be null"));
        }

        SmsSendSingleRequest request = toRequest(message);
        String phoneMasked = maskPhone(message.recipient());

        log.info(
                "SMS outbound request path={} phoneMasked={} contentLogged=false",
                smsProperties.getSendSinglePath(),
                phoneMasked);

        return webClient.post()
                .uri(smsProperties.getSendSinglePath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchangeToMono(response -> mapResponse(response, phoneMasked))
                .onErrorResume(error -> {
                    log.warn(
                            "SMS outbound request failed path={} phoneMasked={} contentLogged=false error={}"
                            , smsProperties.getSendSinglePath(),
                            phoneMasked,
                            error.toString());
                    return Mono.just(new SenderResult(
                            SenderStatus.FAILED,
                            null,
                            "SMS_REQUEST_ERROR",
                            SMS_PROVIDER_FAILURE_MESSAGE));
                });
    }

    private Mono<SenderResult> mapResponse(ClientResponse response, String phoneMasked) {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .thenReturn(new SenderResult(SenderStatus.ACCEPTED, null, null, null));
        }

        String failureCode = "SMS_HTTP_" + response.statusCode().value();
        log.warn(
                "SMS provider returned non-success status={} path={} phoneMasked={} contentLogged=false",
                response.statusCode().value(),
                smsProperties.getSendSinglePath(),
                phoneMasked);
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .thenReturn(new SenderResult(SenderStatus.FAILED, null, failureCode, SMS_PROVIDER_FAILURE_MESSAGE));
    }

    private SmsSendSingleRequest toRequest(SmsMessage message) {
        return new SmsSendSingleRequest(
                smsProperties.getUsername(),
                smsProperties.getPassword(),
                smsProperties.getSendId(),
                smsProperties.getClassCode(),
                resolveCountry(message.country()),
                message.recipient(),
                message.content());
    }

    private String resolveCountry(String country) {
        return StringUtils.hasText(country) ? country : smsProperties.getDefaultCountry();
    }

    private SenderResult validateConfiguration() {
        if (!StringUtils.hasText(smsProperties.getBaseUrl())) {
            return new SenderResult(SenderStatus.FAILED, null, "SMS_CONFIG_BASE_URL_MISSING", SMS_CONFIGURATION_FAILURE_MESSAGE);
        }
        if (!StringUtils.hasText(smsProperties.getUsername())) {
            return new SenderResult(SenderStatus.FAILED, null, "SMS_CONFIG_USERNAME_MISSING", SMS_CONFIGURATION_FAILURE_MESSAGE);
        }
        if (!StringUtils.hasText(smsProperties.getPassword())) {
            return new SenderResult(SenderStatus.FAILED, null, "SMS_CONFIG_PASSWORD_MISSING", SMS_CONFIGURATION_FAILURE_MESSAGE);
        }
        if (!StringUtils.hasText(smsProperties.getSendId())) {
            return new SenderResult(SenderStatus.FAILED, null, "SMS_CONFIG_SEND_ID_MISSING", SMS_CONFIGURATION_FAILURE_MESSAGE);
        }
        if (!StringUtils.hasText(smsProperties.getClassCode())) {
            return new SenderResult(SenderStatus.FAILED, null, "SMS_CONFIG_CLASS_CODE_MISSING", SMS_CONFIGURATION_FAILURE_MESSAGE);
        }
        return null;
    }

    private String maskPhone(String recipient) {
        if (!StringUtils.hasText(recipient)) {
            return "***";
        }
        String trimmed = recipient.trim();
        if (trimmed.length() <= 4) {
            return "***";
        }
        return trimmed.substring(0, 4) + "****";
    }
}