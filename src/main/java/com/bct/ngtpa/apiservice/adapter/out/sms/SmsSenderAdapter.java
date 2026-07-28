package com.bct.ngtpa.apiservice.adapter.out.sms;

import com.bct.ngtpa.apiservice.adapter.out.sms.config.SmsProperties;
import com.bct.ngtpa.apiservice.application.dto.message.SenderResult;
import com.bct.ngtpa.apiservice.application.dto.message.SenderStatus;
import com.bct.ngtpa.apiservice.application.dto.message.SmsMessage;
import com.bct.ngtpa.apiservice.application.port.out.SmsSenderPort;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

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

        String phoneMasked = maskPhone(message.recipient());
        String country = resolveCountry(message.country());

        log.info(
                "SMS outbound debug path={} phoneSuffix={} country={} usernameLength={} passwordLength={} smsContentLength={} smsContentHasNewline={} contentLogged=false",
                smsProperties.getSendSinglePath(),
                phoneSuffix(normalizePhone(message.recipient())),
                country,
                smsProperties.getUsername() == null ? -1 : smsProperties.getUsername().length(),
                smsProperties.getPassword() == null ? -1 : smsProperties.getPassword().length(),
                message.content() == null ? -1 : message.content().length(),
                message.content() != null && (message.content().contains("\n") || message.content().contains("\r")));

        String boundary = multipartBoundary();
        byte[] requestBody = multipartBody(message, country, boundary);

        return webClient.post()
                .uri(smsProperties.getSendSinglePath())
                .header("Accept", "*/*")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .body(BodyInserters.fromValue(requestBody))
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new SenderResult(
                                        SenderStatus.ACCEPTED,
                                        null,
                                        null,
                                        null));
                    }

                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> {
                                log.warn(
                                        "SMS provider returned non-success status={} path={} phoneMasked={} country={} contentLogged=false responseBody={}",
                                        response.statusCode().value(),
                                        smsProperties.getSendSinglePath(),
                                        phoneMasked,
                                        country,
                                        safeProviderResponseBody(body));

                                return new SenderResult(
                                        SenderStatus.FAILED,
                                        null,
                                        "SMS_HTTP_" + response.statusCode().value(),
                                        SMS_PROVIDER_FAILURE_MESSAGE);
                            });
                })
                .onErrorResume(error -> {
                    log.warn(
                            "SMS outbound request failed path={} phoneMasked={} contentLogged=false errorType={} errorMessage={}",
                            smsProperties.getSendSinglePath(),
                            phoneMasked,
                            error.getClass().getSimpleName(),
                            safeErrorMessage(error));

                    return Mono.just(new SenderResult(
                            SenderStatus.FAILED,
                            null,
                            "SMS_REQUEST_ERROR",
                            SMS_PROVIDER_FAILURE_MESSAGE));
                });
    }

    private String multipartBoundary() {
        return "------------------------" + UUID.randomUUID().toString().replace("-", "");
    }

    private byte[] multipartBody(SmsMessage message, String country, String boundary) {
        StringBuilder body = new StringBuilder();

        appendPart(body, boundary, "username", smsProperties.getUsername());
        appendPart(body, boundary, "password", smsProperties.getPassword());
        appendPart(body, boundary, "sendId", smsProperties.getSendId());
        appendPart(body, boundary, "classCode", smsProperties.getClassCode());
        appendPart(body, boundary, "country", country);
        appendPart(body, boundary, "phone", normalizePhone(message.recipient()));
        appendPart(body, boundary, "smsContent", message.content());

        body.append("--").append(boundary).append("--").append("\r\n");
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String phoneSuffix(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "***";
        }
        String trimmed = phone.trim();
        return trimmed.length() <= 4 ? "***" : "****" + trimmed.substring(trimmed.length() - 4);
    }

    private void appendPart(StringBuilder body, String boundary, String name, String value) {
        body.append("--").append(boundary).append("\r\n");
        body.append("Content-Disposition: form-data; name=\"")
                .append(name)
                .append("\"")
                .append("\r\n");
        body.append("\r\n");
        body.append(value == null ? "" : value);
        body.append("\r\n");
    }

    private String safeErrorMessage(Throwable error) {
        if (error == null) {
            return "";
        }
        return "provider error redacted";
        // return error.getMessage()
        // .replace('\n', ' ')
        // .replace('\r', ' ')
        // .replaceAll("\\d{4,}", "****");
    }

    private String resolveCountry(String country) {
        return StringUtils.hasText(country) ? country : smsProperties.getDefaultCountry();
    }

    private SenderResult validateConfiguration() {
        if (!StringUtils.hasText(smsProperties.getBaseUrl())) {
            return new SenderResult(SenderStatus.FAILED, null, "SMS_CONFIG_BASE_URL_MISSING",
                    SMS_CONFIGURATION_FAILURE_MESSAGE);
        }
        if (!StringUtils.hasText(smsProperties.getUsername())) {
            return new SenderResult(SenderStatus.FAILED, null, "SMS_CONFIG_USERNAME_MISSING",
                    SMS_CONFIGURATION_FAILURE_MESSAGE);
        }
        if (!StringUtils.hasText(smsProperties.getPassword())) {
            return new SenderResult(SenderStatus.FAILED, null, "SMS_CONFIG_PASSWORD_MISSING",
                    SMS_CONFIGURATION_FAILURE_MESSAGE);
        }
        if (!StringUtils.hasText(smsProperties.getSendId())) {
            return new SenderResult(SenderStatus.FAILED, null, "SMS_CONFIG_SEND_ID_MISSING",
                    SMS_CONFIGURATION_FAILURE_MESSAGE);
        }
        if (!StringUtils.hasText(smsProperties.getClassCode())) {
            return new SenderResult(SenderStatus.FAILED, null, "SMS_CONFIG_CLASS_CODE_MISSING",
                    SMS_CONFIGURATION_FAILURE_MESSAGE);
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

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.trim();
        if (normalized.startsWith("+852")) {
            return normalized.substring(4);
        }
        if (normalized.startsWith("852") && normalized.length() == 11) {
            return normalized.substring(3);
        }
        return normalized;
    }

    private String safeProviderResponseBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        // return "provider error redacted";
        return body
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\d{4,}", "****");
    }
}